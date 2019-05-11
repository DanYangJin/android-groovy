// Fubei Android Auto Package
// @Author John (linwei@fshows.com)
// @Date 2019-04-11

// 参数定义
// 产品名称
def product = "fubei-android"
// 发布根目录
def distRootDir = ""
// 构建完成日期
def buildDate = ""
// 构建完成时间
def buildTime = ""
// 编译devOpsTaskId
def devOpsTaskId = "nil"
// h5 最后的commit id
def h5RepoLastCommit = "NO"

pipeline {
    //在可用的节点运行
    agent any
    options {
        // 当前任务禁用编译
        disableConcurrentBuilds()
        skipDefaultCheckout()
        timeout(time: 100, unit: 'MINUTES')
        timestamps()
    }
    
    // 自定义参数
    parameters {
        // 以下选择为分支选择，依赖git parameter的jenkins插件
        gitParameter branchFilter: 'origin/(.*)', defaultValue: 'master', name: 'APP_BRANCH', type: 'PT_BRANCH_TAG', description: '选择Android App分支', useRepository: 'http://app-api-test.51youdian.com:9033/Android/new-fubei-android-2.5.git'
        gitParameter branchFilter: 'origin/(.*)', defaultValue: 'master', name: 'H5_BRANCH', type: 'PT_BRANCH_TAG', description: '选择H5 WebApp分支', useRepository: 'http://app-api-test.51youdian.com:9033/web/life-circle-merchant-front.git'
        gitParameter branchFilter: 'origin/(.*)', defaultValue: 'master', name: 'COMMON_BRANCH', type: 'PT_BRANCH_TAG', description: '选择Android公共库分支', useRepository: 'http://app-api-test.51youdian.com:9033/Android/new-fubei-common.git'

        // 钉钉webhook accessToken
        string(defaultValue: "", description: 'Dingtalk webhook accessToken', name: 'DINGTALK_ACCESS_TOKEN')

        // 以下选择根据app(build.gradle)进行配置
        // 定义时请按照gradle task命名规则，首字母需要全部大写
        // 定义编译类型，默认为Pos，如果有新增，请使用choiceParameters
        choice(choices: ['Mobile', 'Pos'], description: 'ProductFlavor Type', name: 'PRODUCT_FLAVOR')
        // 定义编译环境
        choice(choices: ['Debug', 'Beta', 'Release'], description: 'Choose BuildType', name: 'BUILD_TYPE')
        // 定义渠道
        choice(choices: ["Common", "Duoduo", "Ceshi"], description: 'Choose ProductFlavor(Channel)', name: 'SUB_PRODUCT_FLAVOR')
        // 强制编译webApp
        booleanParam(defaultValue: false, description: 'Force rebuild webApp bundle', name: 'WEB_BUNDLE_FORCE_REBUILD')
        // 认证Id
        // string(defaultValue:"$APP_CREDENTIALS_ID", description: 'Jenkins credentialsId (*)', name: 'CREDENTIALS_ID')
    }
    
    environment {
        WEBAPP_BUNDLE_TARGET_LOCATION = "${workspace}/new-fubei-android-2.5/app/src/main/assets/www"
        WEBAPP_BUNDLE_LOCATION = "${workspace}/life-circle-merchant-front/platforms/android/assets/www"
    }
    
    stages {
        // 此场景仅仅用于显示系统变量
        stage ('Environment Precheck') {
            steps {
                script {
                    devOpsTaskId = "${JOB_NAME}-${BUILD_NUMBER}-" + new Date().getTime()
                }
                echo "系统变量检查： PATH=$PATH"
                echo "Android SDK: ANDROID_HOME=$ANDROID_HOME"
                echo "GitSCM: CREDENTIALS_ID=$APP_CREDENTIALS_ID"
                echo "生成本次DEVOPS TASKID => ${devOpsTaskId}"
            }
        }
        
        //拉取Android代码，因为需要将webapp的assets拷贝到apk的assets中，所以需要提前签出
       stage ('Checkout Android Source Code') {
            steps {
                // 签出公共库代码
                echo "checkout new-fubei-common branch:${params.COMMON_BRANCH}"
                //checkout([$class: 'GitSCM', branches: [[name: "${params.COMMON_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "${workspace}/new-fubei-common"], [$class: 'CheckoutOption', timeout: 100], [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 100]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'da68745b-0e8c-479b-b8dc-86756cef388a', url: 'http://app-api-test.51youdian.com:9033/Android/new-fubei-common.git']]])
                checkout([$class: 'GitSCM', branches: [[name: "${params.COMMON_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "./new-fubei-common"], [$class: 'CheckoutOption', timeout: 100], [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 100]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${APP_CREDENTIALS_ID}", url: 'http://app-api-test.51youdian.com:9033/Android/new-fubei-common.git']]])

                // 签出app代码
                echo "checkout new-fubei-android-app ${params.APP_BRANCH}"
                checkout([$class: 'GitSCM', branches: [[name: "${params.APP_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: "./new-fubei-android-2.5"], [$class: 'CheckoutOption', timeout: 100], [$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 100]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${APP_CREDENTIALS_ID}", url: 'http://app-api-test.51youdian.com:9033/Android/new-fubei-android-2.5.git']]])
            }
        }
        
        // 拉取H5代码仓库
        stage ('Checkout H5 Source Code') {
            steps {
                echo "checkout life-circle-merchant-front branch:${params.H5_BRANCH}"
                checkout([$class: 'GitSCM', branches: [[name: "${params.H5_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: './life-circle-merchant-front']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: "${APP_CREDENTIALS_ID}", url: 'http://app-api-test.51youdian.com:9033/web/life-circle-merchant-front.git']]])
                // 签出成功之后，生成www last-commit
                dir ("${workspace}/life-circle-merchant-front") {
                    sh "mkdir -p www && touch www/last-commit"
                }
            }
        }
        
        // 编译WebApp
        stage ('Compile WebApp') {
            when {
                expression {
                    // 获得H5最新的commitId
                    h5RepoLastCommit = sh(returnStdout: true, script: "echo `git -C ${workspace}/life-circle-merchant-front rev-list HEAD -1`").trim()
                    // 获得编译成功是最新的commitId
                    def recordedCommit = sh(returnStdout: true, script: "echo `cat ${workspace}/life-circle-merchant-front/www/last-commit`").trim()
                    return (h5RepoLastCommit != recordedCommit) || params.WEB_BUNDLE_FORCE_REBUILD
                }
            }
            steps {
                // 切换工作路径
                dir("${workspace}/life-circle-merchant-front") {
                    // 安装依赖 todo
                    sh "yarn"
                    // 显示ionic和cordova版本
                    sh "echo ionic版本: `ionic version` cordova版本: `cordova --version`"
                    // 尝试添加 Android platform
                    // sh "ionic cordova platform add android@6.3.0 --nofetch"
                    // 判断是否存在android platform
                    // sh "var=`cordova platform | grep 'android 6.*\$'`;if [[ $var = '' ]]; then ionic cordova platform add android@6.3.0 --nofetch; else echo \"检测到Platform：$var\"; fi"
                    script {
                        // 获得安装平台
                        def installedPlatform = sh(returnStdout: true, script: 'cordova platform | grep "android 6.*\$" | wc -L').trim()
                        echo "${installedPlatform}"

                        if (installedPlatform == 0) {
                            // 检测是否需要重新安装android 6.3.0
                            // def var = String.format("if [[ \"%s\" = \"0\" ]]; then ionic cordova platform add android@6.3.0 --nofetch; fi", installedPlatform)
                            // sh "echo ${var}"
                            sh "ionic cordova platform add android@6.3.0 --nofetch"
                        }

                        // 清空last-commit
                        sh "echo '' | tee www/last-commit"
                        // 编译web资源，打包编译
                        // https://stackoverflow.com/questions/31235373/whats-the-difference-between-ionic-build-and-ionic-prepare
                        sh "ionic cordova prepare android --prod"
                        // 如果存在ios.js则运行
                        sh "if [ -f ios.js ]; then node ios.js md; fi"
                        // 将git仓库最后的commit写入到last-commit文件中
                        sh "mkdir -p www && echo $h5RepoLastCommit > www/last-commit"
                    }

                    // 清空last-commit
                    // sh "echo '' | tee www/last-commit"
                    // 添加完成之后，需要重新增加依赖
                    // todo： 检查ionic 是否添加平台的方法
                    // sh "npm install --registry=https://registry.npm.taobao.org"
                    // 替换VERSION_1_6为VERSION_1_7
                    // sh "sed -i \"s@JavaVersion.VERSION_1_6@JavaVersion.VERSION_1_7@g\" ${workspace}/life-circle-merchant-front/platforms/android/build.gradle"
                    // // 编译web资源
                    // // https://stackoverflow.com/questions/31235373/whats-the-difference-between-ionic-build-and-ionic-prepare
                    // sh "ionic cordova prepare android --prod"
                    // // 如果存在ios.js则运行
                    // sh "if [ -f ios.js ]; then node ios.js md; fi"
                    // // 将git仓库最后的commit写入到last-commit文件中
                    // sh "mkdir -p www && echo $h5RepoLastCommit > www/last-commit"
                }
            }
            
        }

        // 替换WebAppBundle
        stage('Assign WebApp Bundle') {
            steps {
                sh "rm -rf $WEBAPP_BUNDLE_TARGET_LOCATION && mkdir -p $WEBAPP_BUNDLE_TARGET_LOCATION"
                sh "cp -rf $WEBAPP_BUNDLE_LOCATION/* $WEBAPP_BUNDLE_TARGET_LOCATION"
            }
        }
        
        // 编译Android App
        stage ('Compile Android App') {
            steps {
                script {
                    buildTimeStamp = new Date().getTime()
                }

                // 切换工作路径
                dir("${workspace}/new-fubei-android-2.5") {
                    // sh "echo gradle clean assemble${params.PRODUCT_FLAVOR}${params.SUB_PRODUCT_FLAVOR}${params.BUILD_TYPE}"
                    // 因为App将gradle wrapper加入到了.gitignore中，
                    script {
                        if (fileExists ('gradlew')) {
                            sh "chmod +x gradlew"
                            sh "./gradlew clean assemble${params.PRODUCT_FLAVOR}${params.SUB_PRODUCT_FLAVOR}${params.BUILD_TYPE} -PdevOpsTaskId=$devOpsTaskId"
                        } else {
                            sh "gradle clean assemble${params.PRODUCT_FLAVOR}${params.SUB_PRODUCT_FLAVOR}${params.BUILD_TYPE} -PdevOpsTaskId=$devOpsTaskId"
                        }
                    }
                    
                }
            }
        }

        // 发布app 
        // 可以在此步对app进行发布
        stage ('Distribution') {
            steps {
                script {
                    //创建发布目录（请注意权限）
                    distRootDir = "${env.DISTRIBUTION_ROOT_DIR}"
                    date = sh(returnStdout: true, script: "echo `date '+%Y-%m-%d'`").trim()
                    time = sh(returnStdout: true, script: "echo `date '+%H%M%S'`").trim()

                    // 临时目录
                    def subDir = "$product/$date/$params.BUILD_TYPE"
                    def dir0 = "$distRootDir/$subDir"
                    def dirDest = "$dir0/$SUB_PRODUCT_FLAVOR"
                    // 创建最终发布目录
                    sh "mkdir -p $dirDest"
                    // 将编译完成的文件复制至目标文件夹中
                    def apkPath = sh(returnStdout: true, script: "find ${workspace}/new-fubei-android-2.5/app/build/outputs/apk -iname \"*${devOpsTaskId}.apk\"").trim()
                    sh "cp $apkPath $dirDest"
                    // 生成apkurl
                    def apkUrl = "${env.DISTRIBUTION_ROOT_URL}/$subDir/$SUB_PRODUCT_FLAVOR/${devOpsTaskId}.apk"
                    echo "$apkUrl"    

                    // 复制完成，发送钉钉通知
                    if (params.DINGTALK_ACCESS_TOKEN != "") {
                        dingTalk accessToken: params.DINGTALK_ACCESS_TOKEN, imageUrl: '', jenkinsUrl: "$apkUrl?", message: "【${params.PRODUCT_FLAVOR}${params.SUB_PRODUCT_FLAVOR}-${params.BUILD_TYPE}】构建完成，点击下载", notifyPeople: ''
                    }
                }
            }
        }
    }
 }