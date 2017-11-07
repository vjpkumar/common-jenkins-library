package common.uline.jenkins.pipelines

import common.uline.jenkins.steps.CommonPipelineSteps

import com.microsoft.jenkins.containeragents.builders.AciContainerTemplateBuilder
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate
import com.microsoft.jenkins.containeragents.builders.AciCloudBuilder
import com.microsoft.jenkins.containeragents.aci.AciCloud
import com.microsoft.jenkins.containeragents.aci.AciAgent
import com.microsoft.azure.util.AzureCredentials
import com.microsoft.jenkins.containeragents.util.AzureContainerUtils
import com.microsoft.jenkins.azurecommons.telemetry.AppInsightsConstants
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy
import com.microsoft.jenkins.containeragents.strategy.ProvisionRetryStrategy
import com.microsoft.azure.management.Azure
import com.microsoft.azure.management.containerinstance.ContainerGroup
import com.microsoft.jenkins.containeragents.ContainerPlugin
import com.microsoft.jenkins.containeragents.util.Constants

import org.apache.commons.lang3.time.StopWatch

import hudson.model.Label
import jenkins.model.Jenkins
import hudson.slaves.NodeProvisioner
import hudson.Extension
import hudson.model.Computer
import hudson.model.Descriptor
import hudson.model.Item
import hudson.model.Node
import hudson.slaves.Cloud
import hudson.util.ListBoxModel

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeoutException

import java.util.ArrayList
import java.util.Collection
import java.util.Collections
import java.util.HashMap
import java.util.List
import java.util.Map

String autoNodeLabel

private transient ProvisionRetryStrategy provisionRetryStrategy = new ProvisionRetryStrategy()

/**Constructor, called from PipelineBootstrap.createPipelineBuilder().*/
void initialize() {
    echo 'Initializing CommonAzureJenkinsPipeline.'
    
    commonPipelineSteps = new CommonPipelineSteps()
    commonPipelineSteps.initialize()
            
    echo "###################################### GET NODE LABEL DYNAMICALLY ::: START #########################################################"
      
    autoNodeLabel = provisionAzureSlave()
    
    echo "NODE LABEL IS: $autoNodeLabel"
    echo "###################################### GET NODE LABEL DYNAMICALLY ::: END ###########################################################"
}

void privisionAzureSlaveSH() {
    //sh "echo 'Provisioning Slave....'"
    //az container create --image jenkinsci/jnlp-slave --name jenkinsjnlpslave1 --resource-group demoJenkinsResourceGroup --ip-address public --command-line "jenkins-slave -url http://ulinejenkins.eastus.cloudapp.azure.com:8080/ -workDir=/home/jenkins/agent  ac1311a88802ec4b302a2ff619ef83a0d1250a9877f901e13e572f9c5247e40e demolabel1"
    //autoNodeLabel="demolabel1"
}

@NonCPS 
String provisionAzureSlave() {
    
    echo "============================================================================================================"
	    
    echo " HURRAY IN provisionAzureSlave "
    
    String slave
	
    
    
    def baseTemplate = new AciContainerTemplateBuilder()
        .withName("jnlpslave1")
        .withLabel("demolabel1")
        .withImage("jenkinsci/jnlp-slave")
        .withOsType("Linux")
        .withCommand("jenkins-slave -url http://ulinejenkins.eastus.cloudapp.azure.com:8080 ac1311a88802ec4b302a2ff619ef83a0d1250a9877f901e13e572f9c5247e40e testnodename")
        .withRootFs("/home/jenkins")
        .withTimeout(10)
        .withCpu("1")
        .withMemory("1")
        .withOnceRetentionStrategy()
        .addNewPort("80")
        .build()
    
    echo " TEMPLATE NAME: "+baseTemplate.getName()
    echo " TEMPLATE LABEL: "+baseTemplate.getLabel()
    
    def myCloud = new AciCloudBuilder()
        .withCloudName("ulinecloud1")
        .withAzureCredentialsId("azurespn")
        .withResourceGroup("demoJenkinsResourceGroup")
        .addNewTemplateLike(baseTemplate)
            .withName("jnlpslave1")
            .withLabel("demolabel1")
            .endTemplate()
        .build()
    
    Jenkins.getInstance().clouds.add(myCloud)
	
    echo " aciCloud:: NAME:::  "+myCloud.getName()
    echo " aciCloud:: CREDENTIAL ID:::  "+myCloud.getCredentialsId()
    echo " aciCloud:: RESOURCE:::  "+myCloud.getResourceGroup()
    echo " aciCloud:: TEMPLATES:::  "+myCloud.getTemplates()
    echo " aciCloud:: SUBSCRIPTION ID:::  "+AzureCredentials.getServicePrincipal(myCloud.getCredentialsId()).getSubscriptionId()
    
    AciContainerTemplate template = myCloud.getFirstTemplate(null)
    String templateName = template.getName()
    echo "Using ACI Container template: "+templateName
    echo "Containter CPU: "+template.getCpu()
    echo "Containter TIMEOUT: "+template.getTimeout()
	
    
    AciAgent agent1 = new AciAgent(myCloud, template)
    echo "AGENT1 NODE NAME: "+agent1.getNodeName()
	
    /**
    Collection<NodeProvisioner.PlannedNode> plannedNodes = myCloud.provision(null,1) 
    echo " plannedNodes: "+plannedNodes    
    for(NodeProvisioner.PlannedNode plannedNode: plannedNodes){        
        echo " plannedNode Display Name: "+plannedNode.displayName	
        slave = baseTemplate.getLabel()
    }
    */
	
    NodeProvisioner.PlannedNode plannedNode = 
	new NodeProvisioner.PlannedNode(
	    template.getName(), 
	    Computer.threadPoolForRemoting.submit(
		new Callable<Node>() {
		    @Override
		    public Node call() throws Exception {
			AciAgent agent = null;
			final Map<String, String> properties = new HashMap<>();

			try {
			    agent = new AciAgent(myCloud, template);
			    
			    echo "AGENT NODE NAME: "+agent.getNodeName()
			    Jenkins.getInstance().addNode(agent);

			    //start a timeWatcher
			    StopWatch stopWatch = new StopWatch();
			    stopWatch.start();

			    //BI properties
			    properties.put(AppInsightsConstants.AZURE_SUBSCRIPTION_ID,AzureCredentials.getServicePrincipal(myCloud.getCredentialsId()).getSubscriptionId());
			    properties.put(Constants.AI_ACI_NAME, agent.getNodeName());
			    properties.put(Constants.AI_ACI_CPU_CORE, template.getCpu());

			    //Deploy ACI and wait
			    template.provisionAgents(myCloud, agent, stopWatch);

			    //wait JNLP to online
			    waitToOnline(agent, template.getTimeout(), stopWatch);

			    provisionRetryStrategy.success(template.getName());

			    //Send BI
			    ContainerPlugin.sendEvent(Constants.AI_ACI_AGENT, "Provision", properties);

			    return agent
			} catch (Exception e) {
			    echo "EXCEPTION: "+e.getMessage()			    
			    properties.put("Message", e.getMessage());
			    ContainerPlugin.sendEvent(Constants.AI_ACI_AGENT, "ProvisionFailed", properties);

			    if (agent != null) {
				agent.terminate();
			    }

			    provisionRetryStrategy.failure(template.getName());

			    throw new Exception(e)
			}
		    }
		}
		), 1)
    
     return slave
}

public Azure getAzureClient() throws Exception {
	return AzureContainerUtils.getAzureClient("azurespn")
}

private void waitToOnline(AciAgent agent, int startupTimeout, StopWatch stopWatch) throws Exception {	
	echo "WAITING AGENT ONLINE: "+agent.getNodeName()
	Azure azureClient = getAzureClient()

	while (true) {
	    
	   echo "WHILE TRUE : AGENT TOCOMPUTER "+agent.toComputer()
		
	    if (AzureContainerUtils.isTimeout(startupTimeout, stopWatch.getTime())) {
		echo "WHILE TRUE : ACI container connection timeout"    
		throw new TimeoutException("ACI container connection timeout")
	    }

	    if (agent.toComputer() == null) {
		echo "WHILE TRUE : Agent node has been deleted"        
		throw new IllegalStateException("Agent node has been deleted")
	    }
	    ContainerGroup containerGroup =
		    azureClient.containerGroups().getByResourceGroup("demoJenkinsResourceGroup", agent.getNodeName())

	    if (containerGroup.containers().containsKey(agent.getNodeName())
		    && containerGroup.containers().get(agent.getNodeName()).instanceView().currentState().state()
		    .equals("Terminated")) {
		    echo "WHILE TRUE : ACI container terminated"        
		throw new IllegalStateException("ACI container terminated")
	    }

	    if (agent.toComputer().isOnline()) {
		break
	    }
	    final int retryInterval = 5 * 1000
	    Thread.sleep(retryInterval)
	}
}

void triggerCommonAzureJenkinsPipeline() {
	
	node(autoNodeLabel) 
	{					
		stage('Checkout') 
		{			
			
            commonPipelineSteps.checkout()
		}
		
		timeout(time: 15, unit: 'MINUTES')
		{		
			stage('Build'){
				commonPipelineSteps.runMavenBuild()				
			}
  		}
	}
}

return this
