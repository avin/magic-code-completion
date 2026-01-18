package com.c75.magiccodeinsert.services

import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import java.nio.file.FileSystems
import java.nio.file.PathMatcher
import java.nio.file.Paths

@Service(Service.Level.PROJECT)
class CodeMapService(private val project: Project) {
    
    fun generateCodeMap(patterns: List<String>, currentFilePath: String?): String {
        if (patterns.isEmpty()) {
            return ""
        }
        return ReadAction.compute<String, RuntimeException> {
            val builder = StringBuilder()
            builder.append("PROJECT CODE MAP:\n\n")
            
            val projectBasePath = project.basePath ?: return@compute ""
            val psiManager = PsiManager.getInstance(project)
            
            // Collect all matching files
            val matchingFiles = collectMatchingFiles(patterns, projectBasePath)
            
            // Process each file
            for (file in matchingFiles.sortedBy { it.path }) {
                val psiFile = psiManager.findFile(file) ?: continue
                val relativePath = file.path.removePrefix(projectBasePath).removePrefix("/").removePrefix("\\")
                
                val elements = extractPublicElements(psiFile)
                if (elements.isNotEmpty()) {
                    builder.append("File: $relativePath\n")
                    elements.forEach { element ->
                        builder.append(element).append("\n")
                    }
                    builder.append("\n")
                }
            }
            
            if (currentFilePath != null) {
                builder.append("CURRENT FILE: $currentFilePath\n\n")
            }
            
            builder.toString()
        }
    }
    
    private fun collectMatchingFiles(patterns: List<String>, basePath: String): List<VirtualFile> {
        val matchers = patterns.map { pattern ->
            val glob = "glob:$basePath/${pattern.trim()}"
            FileSystems.getDefault().getPathMatcher(glob)
        }
        
        val result = mutableListOf<VirtualFile>()
        val baseVirtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(basePath) ?: return emptyList()
        
        collectFilesRecursively(baseVirtualFile, basePath, matchers, result)
        return result
    }
    
    private fun collectFilesRecursively(
        dir: VirtualFile,
        basePath: String,
        matchers: List<PathMatcher>,
        result: MutableList<VirtualFile>
    ) {
        if (!dir.isDirectory) return
        
        for (child in dir.children) {
            if (child.isDirectory) {
                collectFilesRecursively(child, basePath, matchers, result)
            } else {
                val relativePath = child.path.removePrefix(basePath).removePrefix("/").removePrefix("\\")
                val path = Paths.get(basePath, relativePath)
                if (matchers.any { it.matches(path) }) {
                    result.add(child)
                }
            }
        }
    }
    
    private fun extractPublicElements(psiFile: PsiFile): List<String> {
        val elements = mutableListOf<String>()
        
        // Use Structure View API - works for all languages in all IDEs
        val structureViewBuilder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(psiFile)
        
        if (structureViewBuilder is TreeBasedStructureViewBuilder) {
            try {
                val model = structureViewBuilder.createStructureViewModel(null)
                val root = model.root
                
                // Traverse structure tree
                collectStructureElements(root, elements, 0)
                
                model.dispose()
            } catch (e: Exception) {
                // Fallback: just collect named elements
                collectNamedElements(psiFile, elements)
            }
        } else {
            // Fallback: collect named elements
            collectNamedElements(psiFile, elements)
        }
        
        return elements
    }
    
    private fun collectStructureElements(
        element: Any,
        result: MutableList<String>,
        depth: Int
    ) {
        try {
            val structureViewTreeElement = element as? com.intellij.ide.structureView.StructureViewTreeElement ?: return
            
            val presentation = structureViewTreeElement.presentation
            val text = presentation.presentableText ?: return
            val location = presentation.locationString
            
            // Format: indentation + text + location
            val indent = "  ".repeat(depth)
            val fullText = if (location != null && location.isNotEmpty()) {
                "$indent- $text $location"
            } else {
                "$indent- $text"
            }
            
            result.add(fullText)
            
            // Process children
            val children = structureViewTreeElement.children
            for (child in children) {
                collectStructureElements(child, result, depth + 1)
            }
        } catch (e: Exception) {
            // Skip problematic elements
        }
    }
    
    private fun collectNamedElements(psiFile: PsiFile, result: MutableList<String>) {
        // Fallback: just list all named elements (functions, classes, variables, etc.)
        psiFile.accept(object : PsiRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                
                if (element is PsiNamedElement) {
                    val name = element.name
                    if (name != null && name.isNotEmpty()) {
                        val elementType = element.javaClass.simpleName
                            .replace("Impl", "")
                            .replace("Psi", "")
                        result.add("- $elementType: $name")
                    }
                }
            }
        })
    }
}
