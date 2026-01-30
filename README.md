<div align="center">
  <img src="src/main/resources/META-INF/pluginIcon.svg" alt="Magic Code Completion Icon" width="120" height="120">
  
  # Magic Code Completion
  
  JetBrains IDEs plugin for AI-assisted code completion using OpenAI-compatible APIs with agentic file reading and multi-edit capabilities.
  
  <img src=".github/demo.gif" alt="Demo" width="600">
</div>

## How to Use

### Initial Setup

1. Open **Tools → Magic Code Completion** in your IDE
2. Configure API access to your LLM (we recommend using advanced reasoning models for better code quality)
3. Specify glob patterns for files you want the LLM to see - during completion generation, the LLM will see the list of these files and can read them if needed to expand context

### Using the Plugin

1. Place your cursor at the location where you want code completion
2. Press **Alt+I**
3. The plugin sends your current file (with cursor position) and file tree to the LLM
4. The LLM either requests additional file contents or generates code completion immediately
5. Changes are applied automatically

## Why this plugin?

This plugin is a good addition to the AI tools you already have for development.

### Better than standard autocomplete
- 🌳 Sees entire project structure, not just nearby lines
- 📖 Reads relevant files on demand for context-aware completions
- 🧠 Uses advanced reasoning models instead of basic pattern matching

### More convenient than chat agents
- ⌨️ Single hotkey (Alt+I) at cursor position - no switching windows
- 🎯 Focused action: "complete here" vs. vague multi-file chat requests

## Already have a subscription instead of API?
If you already have subscriptions like Codex, Claude Code, GitHub Copilot (and others), you can use them instead of buying separate API access.  
Use [CLIProxyAPIPlus](https://github.com/router-for-me/CLIProxyAPIPlus) to proxy your subscription into an OpenAI-compatible API.
