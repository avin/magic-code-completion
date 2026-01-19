package com.c75.magiccodeinsert.services

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class FileTreeService(private val project: Project) {
    
    /**
     * Generate file tree for project, optionally filtered by include patterns.
     */
    fun generateFileTree(includePatterns: List<String> = emptyList(), currentFilePath: String? = null): String {
        return ReadAction.compute<String, RuntimeException> {
            val basePath = project.basePath ?: return@compute ""
            val baseVirtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(basePath) ?: return@compute ""
            
            if (includePatterns.isEmpty()) {
                return@compute ""
            }
            
            val settings = com.c75.magiccodeinsert.settings.MagicCodeInsertProjectSettings.getInstance(project).state
            
            val matchers = includePatterns.map { pattern ->
                val glob = "glob:$basePath/${pattern.trim()}"
                FileSystems.getDefault().getPathMatcher(glob)
            }
            
            // First, collect all matching files
            val matchingFiles = mutableListOf<VirtualFile>()
            collectMatchingFiles(baseVirtualFile, basePath, matchers, settings.excludeFiles, settings.excludePatterns, matchingFiles)
            
            // Always include current file if specified (even if not matching patterns)
            if (currentFilePath != null) {
                val currentFullPath = "$basePath/$currentFilePath"
                val currentFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(currentFullPath)
                if (currentFile != null && !matchingFiles.contains(currentFile)) {
                    matchingFiles.add(currentFile)
                }
            }
            
            if (matchingFiles.isEmpty()) {
                return@compute ""
            }
            
            val builder = StringBuilder()
            builder.append("PROJECT FILE TREE:\n\n")
            buildTreeFromFiles(baseVirtualFile, basePath, "", matchingFiles, currentFilePath, builder)
            builder.toString()
        }
    }
    
    private fun collectMatchingFiles(
        dir: VirtualFile,
        basePath: String,
        matchers: List<PathMatcher>,
        excludeFiles: Boolean,
        excludePatterns: List<String>,
        result: MutableList<VirtualFile>
    ) {
        if (!dir.isDirectory) return
        
        for (child in dir.children) {
            val relativePath = child.path.removePrefix(basePath).removePrefix("/").removePrefix("\\")
            
            // Skip excluded files if enabled
            if (excludeFiles && isExcludedFile(relativePath, excludePatterns)) {
                continue
            }
            
            if (child.isDirectory) {
                collectMatchingFiles(child, basePath, matchers, excludeFiles, excludePatterns, result)
            } else {
                // Check if file matches any pattern
                val path = Paths.get(basePath, relativePath)
                if (matchers.any { it.matches(path) }) {
                    result.add(child)
                }
            }
        }
    }
    
    private fun buildTreeFromFiles(
        dir: VirtualFile,
        basePath: String,
        indent: String,
        matchingFiles: List<VirtualFile>,
        currentFilePath: String?,
        builder: StringBuilder
    ) {
        if (!dir.isDirectory) return
        
        // Get children that either match or contain matching files
        val relevantChildren = dir.children.filter { child ->
            if (child.isDirectory) {
                // Include directory if it contains any matching files
                containsMatchingFiles(child, matchingFiles)
            } else {
                // Include file if it's in the matching list
                matchingFiles.contains(child)
            }
        }.sortedWith(
            compareBy<VirtualFile> { !it.isDirectory }
                .thenBy { it.name }
        )
        
        for ((index, child) in relevantChildren.withIndex()) {
            val isLast = index == relevantChildren.size - 1
            val prefix = if (isLast) "└── " else "├── "
            val childIndent = indent + if (isLast) "    " else "│   "
            
            if (child.isDirectory) {
                builder.append(indent).append(prefix).append(child.name).append("/\n")
                buildTreeFromFiles(child, basePath, childIndent, matchingFiles, currentFilePath, builder)
            } else {
                // Show file size and mark current file
                val relativePath = child.path.removePrefix(basePath).removePrefix("/").removePrefix("\\")
                val size = formatFileSize(child.length)
                val marker = if (currentFilePath != null && relativePath == currentFilePath) " ← CURRENT" else ""
                builder.append(indent).append(prefix).append(child.name).append(" ($size)$marker\n")
            }
        }
    }
    
    private fun containsMatchingFiles(dir: VirtualFile, matchingFiles: List<VirtualFile>): Boolean {
        if (!dir.isDirectory) return false
        
        for (child in dir.children) {
            if (matchingFiles.contains(child)) {
                return true
            }
            if (child.isDirectory && containsMatchingFiles(child, matchingFiles)) {
                return true
            }
        }
        
        return false
    }
    
    /**
     * Read file content by path relative to project root.
     */
    fun readFile(relativePath: String): String? {
        return ReadAction.compute<String?, RuntimeException> {
            val basePath = project.basePath ?: return@compute null
            val fullPath = "$basePath/$relativePath"
            
            val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
                .findFileByPath(fullPath) ?: return@compute null
            
            if (virtualFile.isDirectory) {
                return@compute null
            }
            
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@compute null
            psiFile.text
        }
    }
    
    private fun isExcludedFile(relativePath: String, patterns: List<String>): Boolean {
        val normalizedPath = relativePath.replace("\\", "/")
        return patterns.any { pattern ->
            try {
                Regex(pattern).matches(normalizedPath)
            } catch (e: Exception) {
                false
            }
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes}B"
            bytes < 1024 * 1024 -> "${bytes / 1024}KB"
            else -> "${bytes / (1024 * 1024)}MB"
        }
    }
}
