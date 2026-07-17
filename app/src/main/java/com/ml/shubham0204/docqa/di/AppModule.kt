package com.ml.shubham0204.docqa.di

import android.content.ContentResolver
import android.content.Context
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module

/**
 * Koin 依赖注入入口
 */
@Module
@ComponentScan("com.ml.shubham0204.docqa")
class AppModule {
    /**
     * 显式声明 ContentResolver 工厂：Android 系统用来通过 URI 访问其他应用文件的入口
     */
    @Factory
    fun contentResolver(context: Context): ContentResolver = context.contentResolver
}
/**
 * 自动扫描整个 docqa 包下所有带有 Koin 注解的类（如 @Single、@Factory、@KoinViewModel），自动注册为可注入的依赖
 */
