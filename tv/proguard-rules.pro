# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 保留Retrofit和Gson相关类
-keepattributes Signature
-keepattributes *Annotation*

# 保留数据模型类
-keep class top.cywin.onetv.core.data.repositories.user.OnlineUsersResponse {
    public <fields>;
    public <methods>;
}

# 保留Retrofit接口
-keep interface top.cywin.onetv.core.data.repositories.user.ApiService { *; }