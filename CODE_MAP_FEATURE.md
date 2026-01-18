# Code Map Feature

## Overview

The Code Map feature allows the plugin to send a comprehensive overview of your project's structure to the LLM. This helps the LLM understand what functions, classes, and methods are available in your codebase, resulting in more context-aware code completions.

**Universal Support**: Works in **all JetBrains IDEs** (IntelliJ IDEA, WebStorm, PyCharm, PHPStorm, RubyMine, GoLand, etc.) and with **all programming languages** supported by the IDE (JavaScript, TypeScript, Python, PHP, Ruby, Go, Kotlin, Java, etc.).

## How It Works

1. **Configuration**: In Settings → Tools → Magic Code Insert, configure glob patterns to specify which files should be scanned
2. **Scanning**: When you trigger code completion (Alt+I), the plugin scans all matching files
3. **Extraction**: Uses IDE's Structure View API to extract code structure automatically for any language
4. **Formatting**: The code map is formatted and prepended to the user message sent to the LLM

The plugin uses the **Structure View API** - the same technology that powers the IDE's Structure tool window. This means it automatically works with any language that your IDE supports, without any language-specific code.

## Configuration

### Glob Patterns

Add glob patterns in the "Code Map (optional)" textarea in settings, one pattern per line:

```
src/main/**/*.kt
src/main/**/*.java
lib/**/*.kt
```

Patterns are relative to your project root and follow standard glob syntax:
- `**` - matches any number of directories
- `*` - matches any characters in a single path segment
- `*.kt` - matches all Kotlin files
- `src/**/*.java` - matches all Java files under src/ directory

### Example Patterns/Java project:
```
src/main/kotlin/**/*.kt
src/main/java/**/*.java
```

For a JavaScript/TypeScript project (WebStorm):
```
src/**/*.js
src/**/*.ts
lib/**/*.js
```

For a Python project (PyCharm):
```
src/**/*.py
myproject/**/*.py
```

For a PHP project (PHPStorm):
```
src/**/*.php (automatically extracted from IDE's Structure View):

**JavaScript/TypeScript example:**
```
PROJECT CODE MAP:

File: src/services/UserService.js
- class UserService
  - constructor(apiClient)
  - findUser(id)
  - createUser(name, email)

File: src/models/User.js
- class User
  - constructor(data)
  - validate()
  - toJSON()

CURRENT FILE: src/controllers/LoginController.js

[Your code with <<<CURSOR>>> marker]
```

**Python example:**
**All languages supported by your JetBrains IDE**, including but not limited to:

- **JavaScript / TypeScript** (WebStorm, IntelliJ IDEA Ultimate)
- **Python** (PyCharm, IntelliJ IDEA Ultimate)
- **PHP** (PHPStorm, IntelliJ IDEA Ultimate)
- **Ruby** (RubyMine, IntelliJ IDEA Ultimate)
- **Go** (GoLand, IntelliJ IDEA Ultimate)
- **Kotlin / Java** (IntelliJ IDEA)
- **C# / .NET** (Rider)
- **HTML / CSS / XML**
- And any other language with Structure View support

The plugin automatically adapts to the language using IDE's built-in Structure View API.

File: myapp/services/user_service.py
- class UserService
  - __init__(self, db)
  - find_user(self, user_id)
  - create_user(self, name, email)
The plugin uses your IDE's **Structure View** to extract code elements. This means it shows the same information you see in the Structure tool window (View → Tool Windows → Structure).

Common elements across languages:
- Classes and interfaces
- Functions and methods
- Properties and fields
- Constants and variables
- Modules and namespaces
- And more, depending on the language

The extraction is **language-agnostic** - it works with whatever your IDE understands
  - val id: String
  - val name: String
  - val email: String
  - fun validate(): Boolean

CURRENT FILE: src/main/kotlin/com/example/LoginController.kt

[Your code with <<<CURSOR>>> marker]
```
Structure View API (fast, optimized by JetBrains)
- Scans only files matching your patterns
- No persistent caching (relies on IDE's internal cach
- **Kotlin**: Classes, data classes, objects, companion objects, interfaces, functions, properties
- **Java**: Classes, interfaces, enums, annotations, methods, fields

## What Gets Extracted

### Kotlin
- Top-level functions
- Classes (regular, data, sealed)
- Objects and companion objects
- Interfaces
- Public methods with full signatures
- Public properties with types

### Java
- Classes and interfaces
- Enums and annotations
- Public methods with parameter types and return types
- Public fields with types

**Note**: Only public/non-private elements are extracted. Private methods, fields, and internal implementation details are excluded.

## Performance

The code map feature:
- Runs on each Alt+I invocation
- Uses IDE's PSI (Program Structure Interface) and indexes for fast scanning
- Scans only files matching your patterns
- No persistent caching (uses IDE's built-in indexes)

**For Node.js/JavaScript:**
```
src/api/**/*.js
src/services/**/*.js
lib/**/*.js
```

**For Python:**
```
myapp/api/**/*.py
myapp/services/**/*.py
## Best Practices

### 1. Focus on Public APIs
Only include directories with public-facing code:
```
src/main/kotlin/com/myapp/api/**/*.kt
src/main/kotlin/com/myapp/services/**/*.kt
```

Avoi**/*.js
# or
src/**/*.py
# or
src/**/*.php
```

If completions need more context, add:
```**/*.js         # ✓ Include
test/**/*.js        # ✗ Exclude
spec/**/*.js
shared/**/*.ts

If completions need more context, add:
```
frontend/src/**/*.js
frontend/src/**/*.ts
backend/src/**/*.py
shared/**/*.*

### 3. Exclude Test Code
Don't include test directories unless you're working on tests:
```
src/main/**/*.kt        # ✓ Include
src/test/**/*.kt        # ✗ Exclude
```**/*.js` (not `src\**\*.js`)
- Check if Structure View works for those files (View → Tool Windows → Structure

### 4. Multiple Patterns for Mixed Projects
For projects with multiple languages or modules:
```
module-a/src/**/*.kt
module-b/src/**/*.java
shared/src/**/*.kt
```

## Troubleshooting

### Code map is empty
- Check that patterns match your project structure
- Verify files exist at the specified paths
- Ensure patterns use forward slashes: `src/main/**/*.kt` (not `src\main\**\*.kt`)

### Completions are slow
- Reduce the files are being processed (code map should appear in request
- Make patterns more specific (narrow down directories)
- Exclude large directories with many files

### LLM doesn't use code map information
- Ensure patterns are saving correctly (check Settings → Apply)
- Verify the system prompt mentions using available functions
- Check that public APIs are actually public (not private)
different project types:

### JavaScript/TypeScript (WebStorm, VS Code)
**Glob Patterns:**
```
src/**/*.js
src/**/*.ts
lib/**/*.js
```

### Python source files in your project
- Include configuration files if needed
```
myproject/**/*.py
src/**/*.py
```

### PHP (PHPStorm)
**Glob Patterns:**
```
src/**/*.php
app/**/*.php
lib/**/*.php
```

### IntelliJ Plugin Project## Example Configuration

Here's a recommended starting configuration for a typical IntelliJ plugin project:

**Glob Patterns:**
```
src/main/kotlin/**/*.kt
src/main/resources/META-INF/plugin.xml
```

This will:
- Scan all Kotlin source files
- Include your plugin configuration
- Exclude test code
- Exclude build outputs

**System Prompt:**
```
You are a coding assistant. The user will provide code with a <<<CURSOR>>> marker. 
Generate only the code that should be inserted at the cursor position.
API**: Uses IDE's Structure View API (`LanguageStructureViewBuilder`)
- **Language Support**: Automatic - works with any language supported by the IDE
- **Dependencies**: Only requires `com.intellij.modules.platform` (works in all JetBrains IDEs)ons and classes.
Use these when possible to maintain consistency with the existing codebase.
```

## Integration with LLM

The code map is inserted into the user message before your code:

```
{
  "role": "user",
  "content": "PROJECT CODE MAP:\n\n[code map content]\n\nCURRENT FILE: ...\n\n[your code with cursor]"
}
```

This allows the LLM to:
- See available utility functions
- Know what classes/interfaces exist
- Understand method signatures
- Generate compatible code

## Technical Details

- **Service**: `CodeMapService` (project-level service)
- **Dependencies**: Requires Kotlin plugin (org.jetbrains.kotlin)
- **PSI API**: Uses PsiTreeUtil and PsiManager for code analysis
- **File Scanning**: Java NIO PathMatcher for glob pattern matching
