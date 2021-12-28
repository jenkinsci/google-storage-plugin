<!--
 Copyright 2013 Google LLC

 Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 compliance with the License. You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software distributed under the License
 is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 implied. See the License for the specific language governing permissions and limitations under the
 License.
-->
[![Build Status](https://ci.jenkins.io/job/Plugins/job/google-storage-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/google-storage-plugin/job/develop/)
[![Contributors](https://img.shields.io/github/contributors/jenkinsci/google-storage-plugin.svg)](https://github.com/jenkinsci/google-storage-plugin/graphs/contributors)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/google-storage-plugin.svg)](https://plugins.jenkins.io/google-storage-plugin)
[![GitHub release](https://img.shields.io/github/v/tag/jenkinsci/google-storage-plugin?label=changelog)](https://github.com/jenkinsci/google-storage-plugin/blob/develop/CHANGELOG.md)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/google-storage-plugin.svg?color=blue)](https://plugins.jenkins.io/google-storage-plugin)

Jenkins Google Cloud Storage Plugin
===================================

This plugin provides functionality to communicate with Google Cloud Storage, as build steps,
post-build steps, or pipeline steps.

## Documentation
Please see [Google Storage Plugin](docs/home.md) for complete documentation.

## Installation
1. Go to **Manage Jenkins** then **Manage Plugins**.
1. (Optional) Make sure the plugin manager has updated data by clicking the **Check now** button.
1. In the Plugin Manager, click the **Available** tab and look for the "Google OAuth Plugin".
1. Check the box under the **Install** column and click the **Install without restart** button.
1. If the plugin does not appear under **Available**, make sure it appears under **Installed** and
is enabled.
 
## Plugin Source Build Installation
See [Plugin Source Build Installation](docs/source_build_installation.md) to build and install from
source.

## Feature requests and bug reports
Please file feature requests and bug reports as [GitHub Issues](https://github.com/jenkinsci/google-storage-plugin/issues).

**NOTE**: Versions 1.4.0 and above for this plugin are incompatible with version 0.8 or lower of the
[Google OAuth Credentials Plugin](https://github.com/jenkinsci/google-oauth-plugin). Likewise,
versions 1.3.3 and below for this plugin are incompatible with versions 0.9 and above of the OAuth
plugin. 
Please verify you are using the correct versions before filing a bug report.

## Community
The GCP Jenkins community uses the **#gcp-jenkins** slack channel on
[https://googlecloud-community.slack.com](https://googlecloud-community.slack.com)
to ask questions and share feedback. Invitation link available
here: [gcp-slack](https://cloud.google.com/community#home-support).

## License
See [LICENSE](LICENSE)

## Contributing
See [CONTRIBUTING.md](CONTRIBUTING.md)
