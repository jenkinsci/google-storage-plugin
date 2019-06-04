package com.google.jenkins.plugins.storage.IT

pipeline {
    stages {
        stage('Downlaod from GCS') {
            steps{
                step([$class: 'DownloadStep', credentialsId: env
                        .CREDENTIALS_ID,  bucketUri: "gs://${env.BUCKET}/${env.PATTERN}",
                      localDirectory: "test/**/*"])
            }
        }
    }
}