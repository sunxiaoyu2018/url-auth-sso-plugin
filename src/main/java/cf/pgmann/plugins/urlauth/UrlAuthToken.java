package cf.pgmann.plugins.urlauth;

import java.io.IOException;
import java.util.HashMap;

import org.acegisecurity.GrantedAuthority;
import org.acegisecurity.providers.AbstractAuthenticationToken;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.mortbay.util.ajax.JSON;

import hudson.security.SecurityRealm;

public class UrlAuthToken extends AbstractAuthenticationToken {
	private static final long serialVersionUID = 1L;
	private String userName="", displayName="", email="";
	private String cookies;
	private UrlAuthUserDetails user;
	private final long authTime; // the time, in nanoseconds, the auth token was constructed at (just before authentication)

	public UrlAuthToken(String cookies, UrlSecurityRealm realm) {
		super(new GrantedAuthority[]{});
		authTime=System.nanoTime();
		this.cookies = cookies;
		if(realm.targetUrl == null || realm.targetUrl.isEmpty())
			throw new IllegalArgumentException("Please set the authentication Target URL in Jenkins global security configuration.");

		// Try to authenticate using cookies
		HttpGet get = new HttpGet(realm.targetUrl);
		get.addHeader("Cookie", cookies);
		HttpClient client = HttpClientBuilder.create().build();
		HttpResponse response = null;
		String responseText = null;
		try {
			response = client.execute(get);
			responseText = new BasicResponseHandler().handleResponse(response);
		} catch (IOException e) {/*System.out.println("[URL Auth SSO] Failed: "+e.getLocalizedMessage());*/}

		// Check if authentication was successful (200 status code and responseText set)
		if(response != null && response.getStatusLine().getStatusCode() == 200 && responseText != null) {
			//System.out.println("[URL Auth SSO] Target URL response: "+responseText);
			Object obj = JSON.parse(responseText);
			if(obj instanceof HashMap<?, ?>) {
				HashMap<?, ?> json = (HashMap<?, ?>) JSON.parse(responseText);

				// Store downloaded user info
				if(json.containsKey(realm.userNameKey) && json.get(realm.userNameKey) instanceof String) { // userName: REQUIRED
					userName = (String) json.get(realm.userNameKey);
					setAuthenticated(true);
				}
				if(json.containsKey(realm.displayNameKey) && json.get(realm.displayNameKey) instanceof String) { // displayName
					displayName = (String) json.get(realm.displayNameKey);
				} else {
					displayName = userName;
				}
				if(json.containsKey(realm.emailKey) && json.get(realm.emailKey) instanceof String) { // email
					email = (String) json.get(realm.emailKey);
				}
			}
		}
	}

	@Override
	public Object getPrincipal() {
		return userName; // the username
	}
	@Override
	public Object getCredentials() {
		return cookies; // the cookies that identify the user
	}

	@Override
	public GrantedAuthority[] getAuthorities() {
		if(isAuthenticated()) return new GrantedAuthority[]{SecurityRealm.AUTHENTICATED_AUTHORITY};
		else return new GrantedAuthority[]{};
	}

	public UrlAuthUserDetails getUserDetails() {
		if(user==null) user = new UrlAuthUserDetails(userName, displayName, email);
		return user;
	}
	public long getAuthTime() {
		return authTime;
	}

}
