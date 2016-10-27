package scouter.plugin.server.alert.teamup;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.springframework.security.oauth2.common.OAuth2AccessToken;

import com.google.gson.Gson;

import scouter.lang.AlertLevel;
import scouter.lang.TextTypes;
import scouter.lang.pack.AlertPack;
import scouter.lang.pack.ObjectPack;
import scouter.lang.pack.XLogPack;
import scouter.lang.plugin.PluginConstants;
import scouter.lang.plugin.annotation.ServerPlugin;
import scouter.plugin.server.alert.teamup.pojo.Message;
import scouter.plugin.server.alert.teamup.pojo.TeamUpAuth;
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
	final String GRANT_REFRESH = "refresh_token";
	final String GRANT_PASSWORD = "password";
	final String MESSAGE_URL = "https://edge.tmup.com/v3/message/";
	final String OAUTH2_URL = "https://auth.tmup.com/oauth2/token";
	
	private OAuth2AccessToken accessToken;
	
	@ServerPlugin(PluginConstants.PLUGIN_SERVER_ALERT)
	public void alert(final AlertPack pack) {
		if (conf.getBoolean("ext_plugin_teamup_send_alert", false)) {

			// Get log level (0 : INFO, 1 : WARN, 2 : ERROR, 3 : FATAL)
			int level = conf.getInt("ext_plugin_teamup_level", 0);

			if (level <= pack.level) {
				new Thread() {
					public void run() {
						try {
							String roomId = conf.getValue("ext_plugin_teamup_room_id");
							assert roomId != null;
							
							//get access token
							String token = getAccessToken();							
							if(token != null){
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
								HttpPost post = new HttpPost(MESSAGE_URL + roomId);
								post.addHeader("Authorization", "bearer " + token);
								post.addHeader("Content-Type", "application/json");
								post.setEntity(new StringEntity(param));
	
								CloseableHttpClient client = HttpClientBuilder.create().build();
	
								// send teamup message
								HttpResponse response = client.execute(post);
								if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
									println("teamup message sent to [" + roomId + "] successfully.");
								} else {
									println("teamup message sent failed. Verify below information.");
									println("[URL] : " + MESSAGE_URL + roomId);
									println("[StatusCode] : " + response.getStatusLine().getStatusCode());
									println("[Message] : " + param);
									println("[Reason] : " + EntityUtils.toString(response.getEntity(), "UTF-8"));
								}							
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
				String patterns = conf.getValue("ext_plugin_teamup_error_escape_method_patterns").length()>0?conf.getValue("ext_plugin_teamup_error_escape_method_patterns"):"*";
				if (PatternsUtil.isValid(patterns, service)) {
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

	private String getAccessToken() {
		try{
			CloseableHttpClient client = HttpClientBuilder.create().build();
			if(accessToken!=null){
				if(accessToken.isExpired()){
					HttpGet get = new HttpGet(OAUTH2_URL + "?grant_type="+GRANT_REFRESH + "&refresh_token=" + accessToken.getRefreshToken());
					HttpResponse response = client.execute(get);		
					accessToken = new Gson().fromJson(EntityUtils.toString(response.getEntity()), OAuth2AccessToken.class);
				}
			}else{
				String client_id = conf.getValue("ext_plugin_teamup_bot_client_id");
				String client_secret = conf.getValue("ext_plugin_teamup_bot_client_secret");
				String username = conf.getValue("ext_plugin_teamup_bot_username");
				String password = conf.getValue("ext_plugin_teamup_bot_password");
				
				assert client_id != null;
				assert client_secret != null;
				assert username != null;
				assert password != null;
				
				TeamUpAuth auth = new TeamUpAuth(GRANT_PASSWORD, client_id, client_secret, username, password);
				HttpPost post = new HttpPost(OAUTH2_URL);
				post.addHeader("Content-Type", "application/x-www-form-urlencoded");			
				post.setEntity(new StringEntity(new Gson().toJson(auth)));
				HttpResponse response = client.execute(post);		
				accessToken = new Gson().fromJson(EntityUtils.toString(response.getEntity()), OAuth2AccessToken.class);
			}
		}catch(Exception e){
			e.printStackTrace();
			return null;
		}
		
		return accessToken.getValue();
	}
}
