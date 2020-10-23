@Library('jenkins-library' ) _

def pipeline = new org.android.AppPipeline(steps: this, sonar: false, testCmd: 'runTest')
pipeline.runPipeline('fearless')
