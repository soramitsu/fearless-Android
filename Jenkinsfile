@Library('jenkins-library@feature/SUP-8993-fearless-autotests')

// Job properties
def jobParams = [
  booleanParam(defaultValue: false, description: 'push to the dev profile', name: 'prDeployment'),
  booleanParam(defaultValue: false, description: 'Upload builds to nexus(master,develop and staging branches upload always)', name: 'upload_to_nexus'),
]

def pipeline = new org.android.AppPipeline(
    steps:            this,
    sonar:            true,
    sonarCmd:         'sonar -x :core-db:compileDebugUnitTestKotlin -x :core-db:compileDebugAndroidTestKotlin -x :feature-crowdloan-impl:compileDebugAndroidTestKotlin -x :runtime:compileDebugUnitTestKotlin -x :app:kaptDebugAndroidTestKotlin -x :app:compileDebugAndroidTestKotlin -Dsonar.coverage.jacoco.xmlReportPaths=**/coverage/*.xml',
    sonarProjectName: 'fearless-android',
    sonarProjectKey:  'fearless:fearless-android',
    pushReleaseNotes: false,
    testCmd:          'runTest',
    dockerImage:      'build-tools/android-build-box-jdk21:latest',
    publishCmd:       'publishReleaseApk',
    jobParams:        jobParams,
    appPushNoti:      true,
    dojoProductType:  'android',
    uploadToNexusFor: ['master','develop','staging'],
    statusNotif: true,
    triggerAutoTests: true
)
pipeline.runPipeline('fearless')
