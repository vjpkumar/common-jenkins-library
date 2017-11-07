package common.uline.jenkins

import common.uline.jenkins.*
import common.uline.jenkins.pipelines.CommonAzureJenkinsPipeline

def createCommonAzureJenkinsPipeline() {

    def commonAzurePipeline

    node {
        commonAzurePipeline = new CommonAzureJenkinsPipeline()
        commonAzurePipeline.initialize()
    }

    return commonAzurePipeline
}