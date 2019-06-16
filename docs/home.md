<!--
 Copyright 2019 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Google Cloud Storage Plugin Documentation

This plugin provides functionality to communicate with [Google Cloud Storage](https://cloud.google.com/storage), as build steps, post-build steps, or pipeline steps.

## Credentials

All steps consume credentials surfaced by the [Google OAuth Credentials Plugin](https://github.com/jenkinsci/google-oauth-plugin) for authenticating storage requests.  Once you have register a credential (e.g. “my-project”) that provides storage access you will see:

![credentials](images/credentials.png)

If no suitable credentials are found, you will see:

![credential error](images/credentials_error.png)

In this case, you will need to set up credentials in the Credentials tab (see instructions for [Google OAuth Credentials Plugin](https://github.com/jenkinsci/google-oauth-plugin).

## Build Step

Two build steps are supported by this plugin:

* Classic Upload step to upload files to Google Storage.
* Download step to get files from Google Storage into the local Jenkins workspace.
	
![dropdown](images/dropdown.png)

## Classic Upload Build Step

![classic build step](images/classic_build_step.png)

Use the Classic Upload Build Step to upload an ant-style glob of files (File Pattern) to the specified storage path **(Storage Location)**. Select:

* **Share Publicly** to make the uploaded files publicly accessible.
* **Show inline in browser** to set the metadata of the files such that the file is shown inline in browser, rather than downloaded.
* **Strip path prefix** and specify the prefix you want to strip if you don’t want the whole path of the local files reflected in the bucket object name.
	
## Download Build Step

![download build step](images/download_build_step.png)

Use the Download step to download files **(Object to download)** from Cloud Storage into the local directory. The wildcards here act the same way as in GSUtil tool. Currently only a single asterisk at the lowest level of the object name is supported.

If you don't want the whole path of the object to be reflected in the directory structure, select **Strip path prefix** to strip a prefix from the object names.

## Pipeline Step

Both Classic Upload and Download functionality are available through pipelines and can be generated with Pipeline Syntax builder.
Build Log Upload and Bucket with Expiring Elements Lifecycle are available as steps to be run in post in pipelines.

### Example
In this example, we will do a Classic Upload build step with a Build Log Upload step in post-build.
1. Create a file named "Jenkinsfile" in the root of your project.
1. Within your Jenkinsfile add the following:
```groovy
pipeline {
    agent any
    environment {
        CREDENTIALS_ID ='<YOUR_CREDENTIALS_ID>'
        BUCKET = '<YOUR_BUCKET_NAME>'
        PATTERN = '<OBJECT_TO_UPLOAD>'
        LOG = '<NAME_FOR_BUILD_LOG>'
    }
    stages{
        stage('Store to GCS') {
            steps{
                sh '''
                    env > build_environment.txt
                '''
                // If we name pattern build_environment.txt, this will upload build_environment.txt to our GCS bucket.
                step([$class: 'ClassicUploadStep', credentialsId: env.CREDENTIALS_ID,  bucket: "gs://${env.BUCKET}",
                 pattern: env.PATTERN])
            }
        }
    }
    post {
        always {
            // Uploads build log with name log as an object to our GCS bucket.
            step([$class: 'StdoutUploadStep', credentialsId: env.CREDENTIALS_ID,  bucket: "gs://${env.BUCKET}",
                logName: env.LOG])
        }
    }
}
```

## Post-build step

![post build dropdown](images/post_build_dropdown.png)

This plugin provides the “Google Cloud Storage Uploader” post-build step for publishing build artifacts to Google Cloud Storage. Download functionality is not supported, but can be accessed as a Build Step (see above).

Configure the post-build step with any combination of the following sub-steps:

### Bucket with expiring elements lifecycle

Use this step to set a time to live for a given Google Cloud Storage bucket. It will configure the named bucket to delete objects after the specified number of days.

![bucket](images/bucket.png)

### Build log upload

![build log](images/buildlog.png)

This step uploads the contents of the Jenkins build log to the specified storage path.

To configure this operation to upload stdout even if the build fails, check "For failed jobs". To configure the operation to make the uploaded logs publicly accessible, check "Share publicly".

### Classic upload

Classic Upload has the same functionality as the Build Step Classic Upload step. One additional option is "**For failed jobs**". Check this box to do the upload to Google Cloud Storage even if the build fails.

![classic upload](images/classic_upload.png)

