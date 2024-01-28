@Library('jenkins-library@feature/DOPS-2955/update_android_shared_feature')

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
  booleanParam(defaultValue: false, description: 'Upload builds to nexus(master,develop and staging branches upload always)', name: 'upload_to_nexus'),
]

def pipeline = new org.android.AppPipeline(
    steps:            this,
    sonar:            true,
    sonarCommand:     './gradlew sonar -x :core-db:compileDebugUnitTestKotlin -x :core-db:compileDebugAndroidTestKotlin -x :feature-crowdloan-impl:compileDebugAndroidTestKotlin -x :runtime:compileDebugUnitTestKotlin -x :app:kaptDebugAndroidTestKotlin -x :app:compileDebugAndroidTestKotlin -Dsonar.coverage.jacoco.xmlReportPaths=**/coverage/*.xml',
    sonarProjectName: 'fearless-android',
    sonarProjectKey:  'fearless:fearless-android',
    pushReleaseNotes: false,
    testCmd:          'runTest',
    dockerImage:      'build-tools/android-build-box-jdk17:latest',
    publishCmd:       'publishReleaseApk',
    jobParams:        jobParams,
    appPushNoti:      true,
    dojoProductType:  'android',
    uploadToNexusFor: ['master','develop','staging']
)
pipeline.runPipeline('fearless')