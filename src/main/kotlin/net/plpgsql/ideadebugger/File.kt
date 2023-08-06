package net.plpgsql.ideadebugger

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.dummy.DummyCachingFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightVirtualFile
import java.util.*


class PluginFileSystem : DummyCachingFileSystem<PluginFile>(PROTOCOL) {

    object Util {
        private val INSTANCE = Objects.requireNonNull(VirtualFileManager.getInstance().getFileSystem(PROTOCOL))
                as PluginFileSystem

        fun getInstance(): PluginFileSystem = INSTANCE
    }

    companion object {
        const val PROTOCOL: String = "postgres-debugger-plugin"
        const val PROTOCOL_PREFIX: String = "$PROTOCOL://"
    }

    fun add(file: PluginFile): PluginFile {
        runWriteAction {
            this.fileRenamed(file, file.project, "", file.path)
        }
//        publishEvent(SourceLoaded(file.project, FileItem(file)))
        return file
    }

    override fun findFileByPathInner(path: String): PluginFile? {
//        Util1.LOG.debug("findFileByPathInner: $path")
        val oid = path.toLongOrNull() ?: return null
        return null
    }
    override fun doRenameFile(vFile: VirtualFile, newName: String) {}
}

fun pluginFiles(): PluginFileSystem = PluginFileSystem.Util.getInstance()

class PluginFile(val project: Project, val pgFunc: PostgresLib.Function): LightVirtualFile(
    "${pgFunc.namespace}.${pgFunc.name} [${pgFunc.oid}]",
    getPlLanguage(),
    pgFunc.definition
) {
    val isTrigger: Boolean by lazy { computeTrigger() }
    val variables by lazy { computeVariables() }
    val codeRange by lazy { computeCodeRange() }
    private fun computeTrigger(): Boolean = runReadAction {
        PsiManager.getInstance(project).findFile(this)?.let { psi ->
            isTriggerFunction(psi)
        }?: false
    }
    private fun computeCodeRange(): Pair<Int, Int> =
        runReadAction {
            PsiManager.getInstance(project).findFile(this)?.let { psi ->
                codeRange(psi)
            }?: Pair(0, 0)
        }
    private fun computeVariables(): List<FileVariable> =
        runReadAction {
            PsiManager.getInstance(project).findFile(this)?.let { psi ->
                computeFileVariables(psi)
            }?: emptyList()
        }

    override fun getPath(): String = "${pgFunc.oid}"

}

data class FileVariable(val name: String, val isParam: Boolean, val psiElement: PsiElement) {
    val usages by lazy { usagesOfVariable(psiElement) }
}

fun sourceDiffers(file: PluginFile, function: PostgresLib.Function): Boolean =
    file.pgFunc.md5 != function.md5

fun findFileByFrame(frame: PostgresLib.Frame): PluginFile? =
    PluginFileSystem.Util.getInstance().findFileByPath("${frame.func}")

fun findFileByPath(path: String): PluginFile? =
    PluginFileSystem.Util.getInstance().cachedFiles.find { it.path == path }

fun showFile(project: Project, file: PluginFile) =
    runInEdt {
        FileEditorManager.getInstance(project).openFile(file, true, true)
    }