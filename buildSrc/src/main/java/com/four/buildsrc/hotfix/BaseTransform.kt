package com.four.buildsrc.hotfix

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.android.ide.common.internal.WaitableExecutor
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

abstract class BaseTransform() : Transform() {
    companion object {
        private const val TAG = "BaseTransform"
    }
    override fun getName(): String {
        return TAG
    }


    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        //需要处理的数据类型,这里表示class文件
        return TransformManager.CONTENT_CLASS
    }

    override fun isIncremental(): Boolean {
        //是否增量编译
        return false
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        //作用范围
        if (isLibraryModule) {
            return HashSet<QualifiedContent.Scope>().apply {
                this.add(QualifiedContent.Scope.PROJECT)
                this.add(QualifiedContent.Scope.EXTERNAL_LIBRARIES)
            }
        }
        return TransformManager.SCOPE_FULL_PROJECT
    }


    protected var isLibraryModule: Boolean = false

    private val mWaitableExecutor by lazy {
        WaitableExecutor.useGlobalSharedThreadPool().apply {
            waitForTasksWithQuickFail<Any>(true)
        }
    }

    abstract fun getClassVisitor(classWriter: ClassWriter): ClassVisitor

    override fun transform(transformInvocation: TransformInvocation) {
        println("-----------------$name start--------------------")
        super.transform(transformInvocation)
        val startTime = System.currentTimeMillis()
        val transformOutputProvider = transformInvocation.outputProvider
        val isIncremental = transformInvocation.isIncremental

        transformInvocation.inputs.forEach { input ->
            input.directoryInputs.forEach { directoryInput ->
                //处理源码文件
                mWaitableExecutor.execute {
                    processDirectoryInput(directoryInput, transformOutputProvider,isIncremental)
                }
            }

            input.jarInputs.forEach { jarInput ->
                //处理jar
                mWaitableExecutor.execute {
                    processJarInput(jarInput, transformOutputProvider,isIncremental)
                }
            }

        }
        mWaitableExecutor.waitForTasksWithQuickFail<Any>(true)
        val currTime = System.currentTimeMillis()
        println("-----------------$name 执行耗时为 ${(currTime - startTime) / 1000.00} s--------------------")
    }

    private fun processDirectoryInput(directoryInput: DirectoryInput, outputProvider: TransformOutputProvider,isIncremental: Boolean) {
        val directory = outputProvider.getContentLocation(directoryInput.name,directoryInput.contentTypes,directoryInput.scopes,Format.DIRECTORY)
        FileUtils.forceMkdir(directory)
        if (isIncremental) {
            val srcPath = directoryInput.file.absolutePath
            val destPath = directory.absolutePath
            val fileStatusMap = directoryInput.changedFiles
            fileStatusMap.entries.forEach { changeFile ->
                val status = changeFile.value
                val inputFile = changeFile.key
                val destFilePath = inputFile.absolutePath.replace(srcPath,destPath)
                val destFile = File(destFilePath)
                when (status) {
                    Status.CHANGED,Status.ADDED -> {
                        FileUtils.touch(destFile)
                        //单个单个地复制文件
                        transformSingleFile(inputFile, destFile)
                    }
                    Status.REMOVED -> {
                        if (destFile.exists()) {
                            FileUtils.forceDelete(destFile)
                        }
                    }
                    else -> {

                    }
                }
            }
        } else {
            transformDirectory(directoryInput,directory)
        }
    }

    private fun transformDirectory(directoryInput: DirectoryInput,dest: File) {
        val extensions = arrayOf("class")
        //递归地去获取该文件夹下面所有的文件
        val fileList = FileUtils.listFiles(directoryInput.file,extensions,true)
        val outputFilePath = dest.absolutePath
        val inputFilePath = directoryInput.file.absolutePath
        fileList.forEach { inputFile ->
            //println("替换前  file.absolutePath = ${inputFile.absolutePath}")
            val outputFullPath = inputFile.absolutePath.replace(inputFilePath, outputFilePath)
            //println("替换后  file.absolutePath = $outputFullPath")
            val outputFile = File(outputFullPath)
            //创建文件
            FileUtils.touch(outputFile)
            //单个单个地复制文件
            transformSingleFile(inputFile, outputFile)
        }
    }

    private fun processJarInput(jarInput: JarInput, outputProvider: TransformOutputProvider,isIncremental: Boolean) {
        val dest = outputProvider.getContentLocation(jarInput.name,jarInput.contentTypes,jarInput.scopes,Format.JAR)
        if (isIncremental) {
            when(jarInput.status) {
                Status.ADDED,Status.CHANGED -> {
                    transformJar(jarInput,dest)
                }

                Status.REMOVED -> {
                    if (dest.exists()) {
                        FileUtils.forceDelete(dest)
                    }
                }
                else -> {

                }
            }
        } else {
            transformJar(jarInput,dest)
        }
    }

    //abstract fun copyTargetFilePath() : String

    private fun transformJar(jarInput: JarInput, dest: File) {
        if(isJarInputNeedTrace(jarInput.file.name)) {
            val jarFile = JarFile(jarInput.file)
            val jarFileEntries = jarFile.entries()

            val jarOutputStream = JarOutputStream(FileOutputStream(dest))
            jarFileEntries.iterator().forEach { jarEntry ->
                val entryName = jarEntry.name
                val zipEntry = ZipEntry(entryName)
                //读取jar中的输入流
                val inputStream = jarFile.getInputStream(zipEntry)
                jarOutputStream.putNextEntry(zipEntry)
                if (isNeedTraceClass(entryName)) {
                    //执行插桩
                    val classReader = ClassReader(IOUtils.toByteArray(inputStream))
                    val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    classReader.accept(getClassVisitor(classWriter), ClassReader.EXPAND_FRAMES)
                    jarOutputStream.write(classWriter.toByteArray())
                    /*copyTargetFilePath().takeIf { it.isNotEmpty() }?.apply {
                        val target = File(this)
                        target.parentFile.mkdirs()
                        target.outputStream().use {
                            it.write(classWriter.toByteArray())
                        }
                    }*/
                } else {
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                jarOutputStream.closeEntry()
                inputStream.close()

            }
            jarOutputStream.close()
            jarFile.close()
        } else {
            FileUtils.copyFile(jarInput.file,dest)
        }
    }

    //用于筛选jar处理的范围 这里目前只处理多模块下的java/kotlin文件生成的jar
    protected open fun isJarInputNeedTrace(fileName: String): Boolean {
        return fileName == "classes.jar"
    }

    private fun transformSingleFile(inputFile: File, destFile: File) {
        if (isNeedTraceClass(destFile.name)) {
            traceFile(inputFile, destFile)
        } else {
            FileUtils.copyFile(inputFile, destFile)
        }
    }

    protected open fun isNeedTraceClass(name: String):Boolean {
        if (name.startsWith("androidx/")
            || name.startsWith("android/")
            || name.startsWith("kotlin/")
            || name.startsWith("io/reactivex")
            || name.startsWith("com/google")
            || name.startsWith("com/squareup")) {
            return false
        }
        var newName = name
        if (name.contains('/')) {
            val index = name.lastIndexOf('/') + 1
            if (index >= name.length) {
                return false
            }
            newName = name.substring(index)
        }
        return newName.endsWith(".class") &&
                !(newName.startsWith("R$")
                        || newName.startsWith("R.")
                        || newName.contains('$'))
    }

    private fun traceFile(inputFile: File, destFile:File) {
        val inputStream = inputFile.inputStream()
        val outputStream = FileOutputStream(destFile)
        val classReader = ClassReader(inputStream)
        val classWriter = ClassWriter(classReader,ClassWriter.COMPUTE_MAXS)
        classReader.accept(getClassVisitor(classWriter), ClassReader.EXPAND_FRAMES)
        outputStream.write(classWriter.toByteArray())

        inputStream.close()
        outputStream.close()

        /*copyTargetFilePath().takeIf { it.isNotEmpty() }?.apply {
            val target = File(this)
            FileUtils.copyFile(destFile, target)
        }*/
    }
}