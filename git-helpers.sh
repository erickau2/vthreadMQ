#!/bin/bash

# VthreadMQ Git Helper Script
# Common git operations for the project

echo "🚀 VthreadMQ Git Helper"
echo "======================="

# Function to show current status
show_status() {
    echo "📊 Current Git Status:"
    git status --short
    echo ""
    echo "📝 Recent commits:"
    git log --oneline -5
    echo ""
}

# Function to add and commit changes
quick_commit() {
    if [ -z "$1" ]; then
        echo "❌ Please provide a commit message"
        echo "Usage: ./git-helpers.sh commit \"Your commit message\""
        return 1
    fi
    
    echo "📦 Adding all changes..."
    git add .
    
    echo "💾 Committing with message: $1"
    git commit -m "$1"
    
    echo "✅ Commit completed!"
}

# Function to create a new feature branch
new_branch() {
    if [ -z "$1" ]; then
        echo "❌ Please provide a branch name"
        echo "Usage: ./git-helpers.sh branch feature-name"
        return 1
    fi
    
    echo "🌿 Creating new branch: $1"
    git checkout -b "$1"
    echo "✅ Branch created and switched to: $1"
}

# Function to push to remote
push_changes() {
    current_branch=$(git branch --show-current)
    echo "🚀 Pushing changes to remote branch: $current_branch"
    git push -u origin "$current_branch"
    echo "✅ Push completed!"
}

# Main script logic
case "$1" in
    "status"|"s")
        show_status
        ;;
    "commit"|"c")
        quick_commit "$2"
        ;;
    "branch"|"b")
        new_branch "$2"
        ;;
    "push"|"p")
        push_changes
        ;;
    "help"|"h"|"")
        echo "Available commands:"
        echo "  status|s           - Show git status and recent commits"
        echo "  commit|c \"message\" - Add all changes and commit with message"
        echo "  branch|b name      - Create and switch to new branch"
        echo "  push|p             - Push current branch to remote"
        echo "  help|h             - Show this help message"
        echo ""
        echo "Examples:"
        echo "  ./git-helpers.sh status"
        echo "  ./git-helpers.sh commit \"Add new feature\""
        echo "  ./git-helpers.sh branch feature-websocket-improvements"
        echo "  ./git-helpers.sh push"
        ;;
    *)
        echo "❌ Unknown command: $1"
        echo "Run './git-helpers.sh help' for available commands"
        ;;
esac 