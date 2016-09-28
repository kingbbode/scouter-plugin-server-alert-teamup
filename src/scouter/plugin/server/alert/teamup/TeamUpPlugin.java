package scouter.plugin.server.alert.teamup;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import scouter.lang.AlertLevel;
import scouter.lang.TextTypes;
import scouter.lang.pack.AlertPack;
import scouter.lang.pack.ObjectPack;
import scouter.lang.pack.XLogPack;
import scouter.lang.plugin.PluginConstants;
import scouter.lang.plugin.annotation.ServerPlugin;
import scouter.plugin.server.alert.teamup.pojo.Message;
import scouter.plugin.server.alert.teamup.pojo.RefreshToken;
import scouter.plugin.server.alert.teamup.pojo.TokenCheck;
import scouter.plugin.server.alert.teamup.util.PatternsUtil;
import scouter.server.Configure;
import scouter.server.Logger;
import scouter.server.core.AgentManager;
import scouter.server.db.TextRD;
import scouter.util.DateUtil;

public class TeamUpPlugin {
	// Get singleton Configure instance from server
	final Configure conf = Configure.getInstance();
	final int FAILED = 0;
	final int SUCCESS = 1;
	final int TOKEN_EXPIRE = 2;

	@ServerPlugin(PluginConstants.PLUGIN_SERVER_ALERT)
	public void alert(final AlertPack pack) {
		if (conf.getBoolean("ext_plugin_teamup_send_alert", false)) {

			// Get log level (0 : INFO, 1 : WARN, 2 : ERROR, 3 : FATAL)
			int level = conf.getInt("ext_plugin_teamup_level", 0);

			if (level <= pack.level) {
				new Thread() {
					public void run() {
						try {
							// Make a request URL using teamup bot api
							String url = "https://edge.tmup.com/v1/message/";

							// Get server configurations for teamup
							String token = conf.getValue("ext_plugin_teamup_bot_token");
							String refreshToken = conf.getValue("ext_plugin_teamup_bot_refresh_token");
							String roomId = conf.getValue("ext_plugin_teamup_room_id");

							assert token != null;
							assert refreshToken != null;
							assert roomId != null;

							token = expireCheckToken(token, refreshToken);

							// Get the agent Name
							String name = AgentManager.getAgentName(pack.objHash) == null ? "N/A"
									: AgentManager.getAgentName(pack.objHash);

							if (name.equals("N/A") && pack.message.endsWith("connected.")) {
								int idx = pack.message.indexOf("connected");
								if (pack.message.indexOf("reconnected") > -1) {
									name = pack.message.substring(0, idx - 6);
								} else {
									name = pack.message.substring(0, idx - 4);
								}
							}

							String title = pack.title;
							String msg = pack.message;
							if (title.equals("INACTIVE_OBJECT")) {
								title = "An object has been inactivated.";
								msg = pack.message.substring(0, pack.message.indexOf("OBJECT") - 1);
							}

							// Make message contents
							String contents = "[TYPE] : " + pack.objType.toUpperCase() + "\n" + "[NAME] : " + name
									+ "\n" + "[LEVEL] : " + AlertLevel.getName(pack.level) + "\n" + "[TITLE] : " + title
									+ "\n" + "[MESSAGE] : " + msg;

							Message message = new Message(contents);
							String param = new Gson().toJson(message);
							HttpPost post = new HttpPost(url + roomId);
							post.addHeader("Authorization", "bearer " + token);
							post.addHeader("Content-Type", "multipart/form-data");
							post.setEntity(new StringEntity(param));

							CloseableHttpClient client = HttpClientBuilder.create().build();

							// send the post request
							HttpResponse response = client.execute(post);
							if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
								println("teamup message sent to [" + roomId + "] successfully.");
							} else {
								println("teamup message sent failed. Verify below information.");
								println("[URL] : " + url + roomId);
								println("[StatusCode] : " + response.getStatusLine().getStatusCode());
								println("[Message] : " + param);
								println("[Reason] : " + EntityUtils.toString(response.getEntity(), "UTF-8"));
							}
						} catch (Exception e) {
							println("[Error] : " + e.getMessage());

							if (conf._trace) {
								e.printStackTrace();
							}
						}
					}
				}.start();
			}
		}
	}

	@ServerPlugin(PluginConstants.PLUGIN_SERVER_OBJECT)
	public void object(ObjectPack pack) {
		if (pack.version != null && pack.version.length() > 0) {
			AlertPack ap = null;
			ObjectPack op = AgentManager.getAgent(pack.objHash);

			if (op == null && pack.wakeup == 0L) {
				// in case of new agent connected
				ap = new AlertPack();
				ap.level = AlertLevel.INFO;
				ap.objHash = pack.objHash;
				ap.title = "An object has been activated.";
				ap.message = pack.objName + " is connected.";
				ap.time = System.currentTimeMillis();
				ap.objType = "scouter";

				alert(ap);
			} else if (op.alive == false) {
				// in case of agent reconnected
				ap = new AlertPack();
				ap.level = AlertLevel.INFO;
				ap.objHash = pack.objHash;
				ap.title = "An object has been activated.";
				ap.message = pack.objName + " is reconnected.";
				ap.time = System.currentTimeMillis();
				ap.objType = "scouter";

				alert(ap);
			}
			// inactive state can be handled in alert() method.
		}
	}

	@ServerPlugin(PluginConstants.PLUGIN_SERVER_XLOG)
	public void xlog(XLogPack pack) {
		if (conf.getBoolean("ext_plugin_teamup_xlog_enabled", true)) {
			if (pack.error != 0) {
				String date = DateUtil.yyyymmdd(pack.endTime);
				String service = TextRD.getString(date, TextTypes.SERVICE, pack.service);
				if (!PatternsUtil.isValid(conf.getValue("ext_plugin_teamup_error_escape_method_patterns"), service)) {
					AlertPack ap = new AlertPack();
					ap.level = AlertLevel.ERROR;
					ap.objHash = pack.objHash;
					ap.title = "Ultron Error";
					ap.message = service + " - " + TextRD.getString(date, TextTypes.ERROR, pack.error);
					ap.time = System.currentTimeMillis();
					ap.objType = "scouter";
					alert(ap);
				}
			}
		}
	}

	private void println(Object o) {
		if (conf.getBoolean("ext_plugin_teamup_debug", false)) {
			Logger.println(o);
		}
	}

	private String expireCheckToken(String token, String refreshToken) throws Exception {
		HttpGet get = new HttpGet("https://auth.tmup.com/v1?token=" + token);
		// send the get request for token check
		HttpResponse response = HttpClientBuilder.create().build().execute(get);
		TokenCheck check = new Gson().fromJson(EntityUtils.toString(response.getEntity()), TokenCheck.class);
		if (null != check && null != check.getUserIdx() && !"".equals(check.getUserIdx())) {
			return token;
		} else {
			HttpGet refreshGet = new HttpGet(
					"https://auth.tmup.com/oauth2/token?grant_type=refresh_token&refresh_token=" + refreshToken);
			// send the get request for token refresh
			HttpResponse refreshResponse = HttpClientBuilder.create().build().execute(refreshGet);
			RefreshToken refresh = new Gson().fromJson(EntityUtils.toString(refreshResponse.getEntity()),
					RefreshToken.class);
			String newToken = refresh.getAccess_token();
			println("Token Expire, Please ReWrite Token to '" + newToken + "'");
			return newToken;
		}
	}
}
