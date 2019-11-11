<!--
 Copyright 2019 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 compliance with the License. You may obtain a copy of the License at
 
        https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under the License
 is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 implied. See the License for the specific language governing permissions and limitations under the
 License.
-->
# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

## [Unresolved]

 ### Security
 
 ### Added
  
 ### Changed
				
 ### Removed
				 
 ### Fixed
 
## [1.5.1] - 2019-11-11
 ### Security
 - fasterxml.jackson.version changed: 2.9.10 to 2.10.0

## [1.5.0] - 2019-10-30
 ### Changed
 - org.jenkins-ci.plugins:google-oauth-plugin version changed: 0.9 to 1.0.0
 - com.google.guava:guava version changed: 14.0.1 to 20.0
 - com.google.api-client:google-api-client version changed: 1.24.1 to 1.25.0
 - com.google.oauth-client:google-oauth-client version changed: 1.24.1 to 1.25.0
 - com.google.http-client:google-http-client version changed: 1.24.1 to 1.21.0
 - com.google.http-client:google-http-client-jackson2 version changed: 1.24.1 to 1.25.0
 - com.google.apis:google-api-services-storage version changed: v1-rev155-1.24.1 to v1-rev158-1.25.0

 ### Fixed
 - Replaced usages of deprecated `Objects::firstNonNull` with `MoreObjects::firstNonNull` 
 
## [1.4.0] - 2019-09-04
 ### Changed
 - org.jenkins-ci.plugins:google-oauth-plugin version changed: 0.7 to 0.9
 - org.jenkins-ci.plugins:credentials version changed: 2.1.16 to 2.2.0
 - org.jenkins-ci.plugins:ssh-credentials version changed: 1.13 to 1.16
 
 ### Removed
 - StringJsonServiceAccountConfig for tests.
 
 ### Fixed
 - Incompatiblity with version 0.9 of google-oauth-plugin.
 
## [1.3.3] - 2019-08-07
### Security
 - Updated jackson-databind to 2.9.9.2 to address security issues:
   * https://www.cvedetails.com/cve/CVE-2019-14439/
   * https://www.cvedetails.com/cve/CVE-2019-14379/

 ## [1.3.2] - 2019-07-08
### Fixed
 - Issue #82: Resolved IllegalStateException resulting from outdated use of Hudson.instance().
 
## [1.3.1] - 2019-07-03
### Fixed
 - Issue #78: Upload module was not being initialized when restarting Jenkins, causing build steps
 created prior to 1.3.0 to fail with null pointer exceptions, as well as build steps created in
 1.3.0 once Jenkins was restarted. Fixed with PR #79. Users that updated to 1.3.0 should not have
 lost configuration for existing jobs and updating to 1.3.1 is sufficient to fix the issue.

## [1.3.0] - 2019-06-24 
### Added
 - Build step functionality for post-build actions expiring bucket lifecycle and build log upload complete
 with integration testing for all pipeline steps.
 - Pipeline model definition added in POM.
 - Automated code formatting added in POM.
  
### Changed
 - Upgraded Jenkins to 2.164.2 from 1.625.3.
 - Made UploadModule transient to satisfy JEP-200 since Jenkins was upgraded.
 
