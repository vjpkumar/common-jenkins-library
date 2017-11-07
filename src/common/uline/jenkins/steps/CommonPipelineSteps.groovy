package common.uline.jenkins.steps

/** Constructor, called from PipelineBuilder.initialize(). */
void initialize() {
    echo 'Initializing PipelineSteps.'    
}

void setBuildProperties() {	
	properties(
		[
			disableConcurrentBuilds(),
			[$class: 'jenkins.model.BuildDiscarderProperty',strategy: [$class: 'LogRotator', artifactDaysToKeepStr: '', artifactNumToKeepStr: '', numToKeepStr: '20', daysToKeepStr: '']]
		]
	)
}

void cleanWorkspace() {
    sh "echo 'Cleaning workspace'"
    step ([$class: 'WsCleanup'])
}

void checkTools(){
	def mvnHome = tool 'Maven'	
	echo "============================================================================================================"
	sh "echo 'Check Tools'"
	sh "${mvnHome}//bin//mvn -v"
	sh "git --version"
	sh "node -v"
    sh "npm -v"
    sh "npm root"
    sh "npm list -g"
    sh "newman run -h"
    echo "============================================================================================================"    
}

void checkout() {
	sh "echo 'Cleaning workspace'"
    checkout scm
}

void runMavenBuild() {
	def mvnHome = tool 'Maven'
    sh "//usr//bin//mvn -B -U -X clean verify -Dbuild.number=${BUILD_NUMBER} -Dmaven.test.skip=true"      	  
}

def runMavenBuildAndTest() {
	def mvnHome = tool 'Maven'
    
  	sh "//usr//bin//mvn -B -U -X clean verify -Dbuild.number=${BUILD_NUMBER}"
  		  
}

return this