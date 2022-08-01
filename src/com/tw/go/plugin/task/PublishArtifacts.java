package com.tw.go.plugin.task;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import java.text.SimpleDateFormat;

import org.apache.commons.io.IOUtils;
import org.jfrog.artifactory.client.Artifactory;
import org.jfrog.artifactory.client.ArtifactoryClientBuilder;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.exceptions.UnhandledRequestTypeException;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;

@Extension
public class PublishArtifacts implements GoPlugin {

private Map<String, String> checksums = emptyMap();
	
    public final static List<String> SUPPORTED_API_VERSIONS = asList("1.0");
    
    public final static String EXTENSION_NAME = "task";
    public static final int SUCCESS_RESPONSE_CODE = 200;
    public final static String REQUEST_CONFIGURATION = "configuration";
    public final static String REQUEST_CONFIGURATION_2 = "go.plugin-settings.get-configuration";
    public final static String REQUEST_VALIDATION = "validate";
    public final static String REQUEST_VALIDATION_2 = "go.plugin-settings.validate-configuration";
    public final static String REQUEST_TASK_VIEW = "view";
    public final static String REQUEST_TASK_VIEW_2 = "go.plugin-settings.get-view";
    public final static String REQUEST_EXECUTION = "execute";
    public final static String PLUGN_WORK_Dir = "/godata/";

    //Handling GoPluginApiRequest request submitted from Go to Plugin implementation
	public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest)
			throws UnhandledRequestTypeException {
		if (goPluginApiRequest.requestName().equals(REQUEST_CONFIGURATION) || goPluginApiRequest.requestName().equals(REQUEST_CONFIGURATION_2)) {
            return handleConfiguration();
        } else if (goPluginApiRequest.requestName().equals(REQUEST_VALIDATION) || goPluginApiRequest.requestName().equals(REQUEST_VALIDATION_2)) {
            return handleValidation(goPluginApiRequest);
        } else if (goPluginApiRequest.requestName().equals(REQUEST_TASK_VIEW) || goPluginApiRequest.requestName().equals(REQUEST_TASK_VIEW_2)) {
            try {
                return handleView();
            } catch (IOException e) {
                String message = "Failed to find template: " + e.getMessage();
                return renderJSON(500, message);
            }
        } else if (goPluginApiRequest.requestName().equals(REQUEST_EXECUTION)) {
            try {
				return handleExecute(goPluginApiRequest);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return null;
	}

	//Initializing an instance of GoApplicationAccessor to allow plugin to communicate with Go Server
	public void initializeGoApplicationAccessor(GoApplicationAccessor goapplicationAccessor) {

	}
	
	//Creating an instance of GoPluginIdentifier to provide details about supported version and extension point 
	public GoPluginIdentifier pluginIdentifier() {		
		return new GoPluginIdentifier(EXTENSION_NAME, SUPPORTED_API_VERSIONS);
	}
	
	//Handling the configuration of fields that are displayed in the UI side.
	private GoPluginApiResponse handleConfiguration() {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("ArtifactPath", createField("ArtifactPath", "", true, false, "1"));
        response.put("TargetRepository", createField("TargetRepository", "", true, false, "0"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }
	
	//Creating fields that need to configured at UI side. 
	private Map<String, Object> createField(String displayName, String defaultValue, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<String, Object>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }
	
	//Rendering the response sent from Plugin to Go Server in Json format
	private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }
            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }
            @Override
            public String responseBody() {
                return json;
            }
        };
    }
	
	//validating the response from plugin  
	private GoPluginApiResponse handleValidation(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> response = new HashMap<String, Object>();
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }
	
	//handling the display names and html form  of the Plugin 
	private GoPluginApiResponse handleView() throws IOException {
        Map<String, Object> response = new HashMap<String, Object>();
        response.put("displayValue", "Artifactory Plugin");
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/views/task.artifactory.html"), "UTF-8"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }
	
	//Executing the requests inbound to plugin 
	private GoPluginApiResponse handleExecute(GoPluginApiRequest goPluginApiRequest) throws IOException{
		Map<String, Object> response = new HashMap<String, Object>();
		
		Map<String, Object> map = (Map<String, Object>) new GsonBuilder().create().fromJson(goPluginApiRequest.requestBody(), Object.class);
		Map<String, Object> configKeyValuePairs = (Map<String, Object>) map.get("config");
		
		//Fetching Working Directory from context
		Map<String, Object> context = (Map<String, Object>) map.get("context");
		String workingDirectory = (String) context.get("workingDirectory");
		JobConsoleLogger.getConsoleLogger().printLine("workingDirectory: " + workingDirectory);		
		
		//Fetching Environment Variables
		Map<String, String> environmentVariables = (Map<String, String>) context.get("environmentVariables");
		String Ev_Url = environmentVariables.get("ARTIFACTORY_URL");
		String Ev_User = environmentVariables.get("ARTIFACTORY_USER");
		String Ev_Pass = environmentVariables.get("ARTIFACTORY_PASSWORD");
		String Ev_Piple = environmentVariables.get("GO_PIPELINE_NAME");
		String Ev_Branch = environmentVariables.get("GO_MATERIAL_BRANCH");
		String Ev_Version = environmentVariables.get("GO_REVISION");

		JobConsoleLogger.getConsoleLogger().printLine("PipleName" + ":" + Ev_Piple);
		JobConsoleLogger.getConsoleLogger().printLine("BranchName" + ":" + Ev_Branch);
		JobConsoleLogger.getConsoleLogger().printLine("VersionName" + ":" + Ev_Version);

		//Fetching Artifact path from Environment Variables
		Map<String, String> ArtifactLocation = (Map<String, String>) configKeyValuePairs.get("ArtifactPath");
		String ArtifactPATH = ArtifactLocation.get("value");
		JobConsoleLogger.getConsoleLogger().printLine("path" + ":" + ArtifactPATH);
		
		//Fetching Repository name from Environment Variables
		Map<String, String> RepositoryName = (Map<String, String>) configKeyValuePairs.get("TargetRepository");
		String Repository = RepositoryName.get("value");
		
		String Local_path = PLUGN_WORK_Dir + workingDirectory + "/" + ArtifactPATH;

		//设置日期格式
		SimpleDateFormat sdf = new SimpleDateFormat("yyMMddHHmmss");
		String time = sdf.format(new Date());
		JobConsoleLogger.getConsoleLogger().printLine("time" + ":" + time);

		String Target_path = Ev_Piple + "_" + Ev_Branch + "/" + Ev_Version + "_" + time + "_" + ArtifactPATH;
		
		//Invoking the upload method to upload the artifacts 
		upload(Ev_Url, Ev_User, Ev_Pass, Local_path, Repository, Target_path);
		response.put("success", true);
		return renderJSON(SUCCESS_RESPONSE_CODE, response);
		}
	//Method to Upload artifacts to artifactory
	private void upload(String url,String username, String pass, String lpath, String repo, String rpath) throws IOException {	
		//Connecting to Artifactory
		Artifactory artifactory= artifactory = ArtifactoryClientBuilder.create()
        .setUrl(url).setUsername(username).setPassword(pass).build();
		
		JobConsoleLogger.getConsoleLogger().printLine("Uri:" + artifactory.getUri());
		JobConsoleLogger.getConsoleLogger().printLine("Username:" + artifactory.getUsername());
		
		//Uploading artifacts to artifactory
		File file = new File(lpath);
		org.jfrog.artifactory.client.model.File deployed =  artifactory.repository(repo)
		    .upload(rpath, file)
		    .doUpload();
	}
}
