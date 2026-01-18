package com.c75.magiccodeinsert.services

import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.lang.LanguageStructureViewBuilder
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
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
            val settings = com.c75.magiccodeinsert.settings.MagicCodeInsertSettings.getInstance().state
            
            // Collect all matching files
            val matchingFiles = collectMatchingFiles(
                patterns, 
                projectBasePath, 
                settings.excludeFiles,
                settings.excludePatterns
            )
            
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
    
    private fun collectMatchingFiles(
        patterns: List<String>, 
        basePath: String, 
        excludeFiles: Boolean,
        excludePatterns: List<String>
    ): List<VirtualFile> {
        val matchers = patterns.map { pattern ->
            val glob = "glob:$basePath/${pattern.trim()}"
            FileSystems.getDefault().getPathMatcher(glob)
        }
        
        val result = mutableListOf<VirtualFile>()
        val baseVirtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .findFileByPath(basePath) ?: return emptyList()
        
        collectFilesRecursively(baseVirtualFile, basePath, matchers, result, excludeFiles, excludePatterns)
        return result
    }
    
    private fun collectFilesRecursively(
        dir: VirtualFile,
        basePath: String,
        matchers: List<PathMatcher>,
        result: MutableList<VirtualFile>,
        excludeFiles: Boolean,
        excludePatterns: List<String>
    ) {
        if (!dir.isDirectory) return
        
        for (child in dir.children) {
            if (child.isDirectory) {
                collectFilesRecursively(child, basePath, matchers, result, excludeFiles, excludePatterns)
            } else {
                val relativePath = child.path.removePrefix(basePath).removePrefix("/").removePrefix("\\")
                
                // Skip excluded files if enabled
                if (excludeFiles && isExcludedFile(relativePath, excludePatterns)) {
                    continue
                }
                
                val path = Paths.get(basePath, relativePath)
                if (matchers.any { it.matches(path) }) {
                    result.add(child)
                }
            }
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
    
    private fun extractPublicElements(psiFile: PsiFile): List<String> {
        val elements = mutableListOf<String>()
        
        // Try JS/TS specific extraction if available
        val fileName = psiFile.name.lowercase()
        if (fileName.endsWith(".js") || fileName.endsWith(".ts") || 
            fileName.endsWith(".jsx") || fileName.endsWith(".tsx")) {
            if (tryExtractJsExports(psiFile, elements)) {
                return elements
            }
            // If extraction failed, fallback to Structure View
        }
        
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
    
    /**
     * Try to extract exports from JS/TS files using regex patterns.
     * Returns true if successfully extracted, false if failed.
     */
    private fun tryExtractJsExports(psiFile: PsiFile, result: MutableList<String>): Boolean {
        try {
            val text = psiFile.text
            val items = mutableListOf<ExportItem>()
            
            // Extract all exported items with their details
            extractExportedClasses(text, items)
            extractExportedFunctions(text, items)
            extractExportedInterfaces(text, items)
            extractExportedTypes(text, items)
            extractExportedEnums(text, items)
            extractExportedConstants(text, items)
            extractNamedExports(text, items)
            extractDefaultExports(text, items)
            extractInternalTypes(text, items)
            
            if (items.isEmpty()) {
                return false
            }
            
            // Build result with proper formatting
            items.sortedBy { it.name.replace(" (default)", "").replace(" (internal)", "") }.forEach { item ->
                result.add("- ${item.name}")
                item.details.forEach { detail ->
                    result.add("  $detail")
                }
            }
            
            return true
        } catch (e: Exception) {
            return false
        }
    }
    
    private data class ExportItem(
        val name: String,
        val details: MutableList<String> = mutableListOf()
    )
    
    private fun extractExportedClasses(text: String, items: MutableList<ExportItem>) {
        val pattern = Regex("""export\s+(?:default\s+)?(?:abstract\s+)?class\s+([A-Z_][a-zA-Z0-9_]*)""")
        pattern.findAll(text).forEach { match ->
            val className = match.groupValues[1]
            val item = ExportItem(className)
            
            // Extract methods with signatures
            val methods = extractClassMethods(text, className)
            item.details.addAll(methods)
            
            items.add(item)
        }
    }
    
    private fun extractExportedFunctions(text: String, items: MutableList<ExportItem>) {
        // Pattern: export function name(params): ReturnType
        val pattern = Regex("""export\s+(?:default\s+)?(?:async\s+)?function\s+([A-Z][a-zA-Z0-9_]*)\s*\(([^)]*)\)\s*(?::\s*([^{;]+))?""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val params = match.groupValues[2].trim()
            val returnType = match.groupValues[3].trim().takeIf { it.isNotEmpty() }
            
            val signature = if (returnType != null) {
                "($params): $returnType"
            } else {
                "($params)"
            }
            
            val item = ExportItem("$name$signature")
            extractJsDoc(text, match.range.first, item)
            items.add(item)
        }
    }
    
    private fun extractExportedInterfaces(text: String, items: MutableList<ExportItem>) {
        val pattern = Regex("""export\s+(?:default\s+)?interface\s+([A-Z][a-zA-Z0-9_]*)\s*(?:extends\s+[^{]+)?\s*\{""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val item = ExportItem(name)
            
            // Extract interface fields
            val fields = extractInterfaceFields(text, match.range.last)
            item.details.addAll(fields)
            
            extractJsDoc(text, match.range.first, item)
            items.add(item)
        }
    }
    
    private fun extractExportedTypes(text: String, items: MutableList<ExportItem>) {
        val pattern = Regex("""export\s+(?:default\s+)?type\s+([A-Z][a-zA-Z0-9_]*)\s*=\s*([^;\n]+)""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val definition = match.groupValues[2].trim()
            
            // Check if it's an object type
            if (definition.startsWith("{")) {
                val item = ExportItem(name)
                val fields = extractInterfaceFields(text, match.range.first + match.value.indexOf('{'))
                item.details.addAll(fields)
                extractJsDoc(text, match.range.first, item)
                items.add(item)
            } else {
                // Type alias
                val item = ExportItem("$name = $definition")
                extractJsDoc(text, match.range.first, item)
                items.add(item)
            }
        }
    }
    
    private fun extractExportedEnums(text: String, items: MutableList<ExportItem>) {
        val pattern = Regex("""export\s+(?:default\s+)?(?:const\s+)?enum\s+([A-Z][a-zA-Z0-9_]*)\s*\{""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val item = ExportItem("$name (enum)")
            
            // Extract enum values
            val values = extractEnumValues(text, match.range.last)
            item.details.addAll(values)
            
            extractJsDoc(text, match.range.first, item)
            items.add(item)
        }
    }
    
    private fun extractExportedConstants(text: String, items: MutableList<ExportItem>) {
        // Pattern: export const NAME = value
        val pattern = Regex("""export\s+const\s+([A-Z_][A-Z0-9_]*)\s*(?::\s*[^=]+)?\s*=\s*([^;\n]+)""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            val value = match.groupValues[2].trim()
            
            val item = ExportItem("$name = $value")
            extractJsDoc(text, match.range.first, item)
            items.add(item)
        }
    }
    
    private fun extractNamedExports(text: String, items: MutableList<ExportItem>) {
        val pattern = Regex("""export\s*\{([^}]+)\}""")
        pattern.findAll(text).forEach { match ->
            val names = match.groupValues[1]
                .split(",")
                .map { it.trim().split(" as ").first().trim() }
                .filter { it.isNotEmpty() && it[0].isUpperCase() }
            
            names.forEach { name ->
                if (items.none { it.name.startsWith(name) }) {
                    items.add(ExportItem(name))
                }
            }
        }
    }
    
    private fun extractDefaultExports(text: String, items: MutableList<ExportItem>) {
        val pattern = Regex("""export\s+default\s+([A-Z][a-zA-Z0-9_]*)(?:\s|;|$)""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (items.none { it.name == name || it.name.startsWith("$name(") }) {
                items.add(ExportItem("$name (default)"))
            }
        }
    }
    
    private fun extractInternalTypes(text: String, items: MutableList<ExportItem>) {
        // Props/State interfaces
        val pattern = Regex("""(?:interface|type)\s+(I?[A-Z][a-zA-Z0-9]*(?:Props|State|Config|Options))""")
        pattern.findAll(text).forEach { match ->
            val name = match.groupValues[1]
            if (items.none { it.name.startsWith(name) }) {
                items.add(ExportItem("$name (internal)"))
            }
        }
    }
    
    private fun extractJsDoc(text: String, position: Int, item: ExportItem) {
        // Find JSDoc comment before the position
        val before = text.substring(0, position)
        val jsdocPattern = Regex("""/\*\*([\s\S]*?)\*/\s*$""")
        val match = jsdocPattern.find(before) ?: return
        
        val comment = match.groupValues[1]
            .lines()
            .map { it.trim().removePrefix("*").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("@") }
            .joinToString(" ")
            .take(100)
        
        if (comment.isNotEmpty()) {
            item.details.add(0, "// $comment")
        }
    }
    
    private fun extractInterfaceFields(text: String, startPos: Int): List<String> {
        val fields = mutableListOf<String>()
        
        try {
            var braceCount = 1
            var endPos = startPos
            
            // Find matching closing brace
            for (i in startPos + 1 until text.length) {
                when (text[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            endPos = i
                            break
                        }
                    }
                }
            }
            
            if (endPos > startPos) {
                val body = text.substring(startPos + 1, endPos)
                
                // Pattern: fieldName?: type
                val fieldPattern = Regex("""([a-zA-Z_][a-zA-Z0-9_]*)\s*\??\s*:\s*([^;,\n]+)""")
                fieldPattern.findAll(body).forEach { match ->
                    val fieldName = match.groupValues[1]
                    val fieldType = match.groupValues[2].trim()
                    fields.add("- $fieldName: $fieldType")
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
        
        return fields
    }
    
    private fun extractEnumValues(text: String, startPos: Int): List<String> {
        val values = mutableListOf<String>()
        
        try {
            var braceCount = 1
            var endPos = startPos
            
            // Find matching closing brace
            for (i in startPos + 1 until text.length) {
                when (text[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            endPos = i
                            break
                        }
                    }
                }
            }
            
            if (endPos > startPos) {
                val body = text.substring(startPos + 1, endPos)
                
                // Pattern: VALUE = "string" or VALUE = number or just VALUE
                val valuePattern = Regex("""([A-Z_][A-Z0-9_]*)\s*(?:=\s*([^,\n]+))?""")
                valuePattern.findAll(body).forEach { match ->
                    val valueName = match.groupValues[1].trim()
                    val valueAssignment = match.groupValues[2].trim()
                    
                    if (valueName.isNotEmpty()) {
                        if (valueAssignment.isNotEmpty()) {
                            values.add("- $valueName = $valueAssignment")
                        } else {
                            values.add("- $valueName")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore errors
        }
        
        return values
    }
    
    /**
     * Extract public methods from a class definition with full signatures.
     */
    private fun extractClassMethods(text: String, className: String): MutableList<String> {
        val methods = mutableListOf<String>()
        
        try {
            // Find class body
            val classPattern = Regex("""class\s+$className[^{]*\{""")
            val classMatch = classPattern.find(text) ?: return methods
            
            val classStart = classMatch.range.last
            var braceCount = 1
            var classEnd = classStart
            
            // Find matching closing brace
            for (i in classStart + 1 until text.length) {
                when (text[i]) {
                    '{' -> braceCount++
                    '}' -> {
                        braceCount--
                        if (braceCount == 0) {
                            classEnd = i
                            break
                        }
                    }
                }
            }
            
            if (classEnd > classStart) {
                val classBody = text.substring(classStart, classEnd)
                
                // Pattern for methods with signatures: async? methodName(params): ReturnType
                val methodPattern = Regex("""(?:(?:public|protected|async|static)\s+)*(?!private|constructor)([a-z][a-zA-Z0-9_]*)\s*\(([^)]*)\)\s*(?::\s*([^{;]+))?""")
                
                val foundMethods = mutableSetOf<String>()
                methodPattern.findAll(classBody).forEach { match ->
                    val methodName = match.groupValues[1]
                    val params = match.groupValues[2].trim()
                    val returnType = match.groupValues[3].trim().takeIf { it.isNotEmpty() }
                    
                    // Skip common non-methods
                    if (methodName !in setOf("if", "for", "while", "switch", "catch", "return")) {
                        val signature = if (returnType != null) {
                            "$methodName($params): $returnType"
                        } else {
                            "$methodName($params)"
                        }
                        foundMethods.add("- $signature")
                    }
                }
                
                methods.addAll(foundMethods.sorted())
            }
        } catch (e: Exception) {
            // If extraction failed, return empty list
        }
        
        return methods
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
