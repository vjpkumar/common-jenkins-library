package common.vj.jenkins

import common.vj.jenkins.*
import common.vj.jenkins.pipelines.CommonAzureJenkinsPipeline

def createCommonAzureJenkinsPipeline() {

    def commonAzurePipeline

    node {
        commonAzurePipeline = new CommonAzureJenkinsPipeline()
        commonAzurePipeline.initialize()
    }

    return commonAzurePipeline
}
