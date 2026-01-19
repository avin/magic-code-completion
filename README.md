# Magic Code Insert

IntelliJ IDEA plugin for AI-assisted code completion using OpenAI-compatible APIs with agentic file reading and multi-edit capabilities.

## Features

- **Alt+I hotkey** - Quick code completion at cursor position
- **Agentic file access** - LLM can dynamically request and read project files
- **Multi-edit support** - Apply multiple code changes in a single request
- **Auto-formatting** - Automatically formats inserted/modified code
- **Project-aware** - File tree context with current file marker
- **Configurable** - Customizable API endpoint, model, prompts, and file patterns
- **Debug mode** - Optional request/response logging for troubleshooting

## Settings

**Tools → Magic Code Insert**

### API Configuration (Global)
- API Endpoint (default: OpenAI)
- API Key
- Model name
- System prompt
- Temperature, max tokens
- Timeouts
- Debug mode

### Project Files (Project-specific)
- Include patterns - glob patterns for files to include in context
- Exclude patterns - regex patterns for test files and other exclusions

## How it works

1. Press **Alt+I** in any editor
2. Plugin sends current file with `<<<CURSOR>>>` marker + file tree to LLM
3. LLM can call functions:
   - `read_file(path)` - read additional project files
   - `apply_edits(edits)` - apply code changes (imports, cursor insertion, refactoring)
4. Changes are applied and auto-formatted

## Function Calling

The plugin uses OpenAI function calling API with two tools:

**read_file(path)**
```json
{
  "name": "read_file",
  "parameters": {
    "path": "src/components/Button.tsx"
  }
}
```

**apply_edits(edits)**
```json
{
  "edits": [
    {
      "search": "import React from 'react'",
      "replace": "import React from 'react'\nimport { useState } from 'react'"
    },
    {
      "search": "<<<CURSOR>>>",
      "replace": "const [count, setCount] = useState(0)"
    }
  ]
}
```

## Building

```bash
./gradlew buildPlugin
```

Plugin zip will be in `build/distributions/`

## Development

- Kotlin 2.1.20
- IntelliJ Platform SDK 2025.2.4
- Gradle 9.0.0
- OkHttp 4.12.0, Gson 2.10.1

## License

[Add your license here]
