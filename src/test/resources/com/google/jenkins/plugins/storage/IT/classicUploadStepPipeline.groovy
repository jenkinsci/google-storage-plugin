package com.google.jenkins.plugins.storage.IT

pipeline {
    agent any
    stages{
        stage('Store to GCS') {
            steps{
                sh '''
                    env > build_environment.txt
                '''
                step([$class: 'ClassicUploadStep', credentialsId: env
                        .CREDENTIALS_ID,  bucket: "gs://${env.BUCKET}",
                      pattern: env.PATTERN])
            }
        }
    }
}