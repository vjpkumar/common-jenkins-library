package common.uline.jenkins.pipelines

import common.uline.jenkins.steps.CommonPipelineSteps

import com.microsoft.jenkins.containeragents.builders.AciContainerTemplateBuilder
import com.microsoft.jenkins.containeragents.aci.AciContainerTemplate
import com.microsoft.jenkins.containeragents.builders.AciCloudBuilder
import com.microsoft.jenkins.containeragents.aci.AciCloud
import com.microsoft.jenkins.containeragents.aci.AciAgent
import com.microsoft.jenkins.containeragents.strategy.ContainerOnceRetentionStrategy
import com.microsoft.azure.management.Azure

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

import java.util.ArrayList
import java.util.Collection

String autoNodeLabel

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
    
    
    echo " aciCloud:: NAME:::  "+myCloud.getName()
    echo " aciCloud:: TEMPLATES:::  "+myCloud.getTemplates()
    
    AciContainerTemplate template = myCloud.getFirstTemplate(null)
    String templateName = template.getName()
    echo "Using ACI Container template: "+templateName
    
    AciAgent agent = new AciAgent(myCloud, template)
    echo "AGENT NODE NAME: "+agent.getNodeName()
	
    Collection<NodeProvisioner.PlannedNode> plannedNodes = myCloud.provision(null,1) 
    echo " plannedNodes: "+plannedNodes
    
    for(NodeProvisioner.PlannedNode plannedNode: plannedNodes){
        
        echo " plannedNode Display Name: "+plannedNode.displayName
        
        //slave = plannedNode.displayName
        slave = baseTemplate.getLabel()
    }
        
    return slave
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
