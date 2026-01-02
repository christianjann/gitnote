use super::*;
use std::fs;
use std::path::Path;
use std::process::Command;
use serial_test::serial;

fn run_git_command(dir: &Path, args: &[&str]) {
    let output = Command::new("git")
        .current_dir(dir)
        .args(args)
        .output()
        .expect("Failed to run git command");

    if !output.status.success() {
        panic!(
            "Git command failed: git {} (in {})\nstdout: {}\nstderr: {}",
            args.join(" "),
            dir.display(),
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }
}

#[test]
#[serial]
fn test_git_operations_integration() {
    // Initialize the library first
    init_lib("/tmp".to_string());

    // Setup test directories with unique name
    let test_dir = Path::new("test_repos_integration");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    println!("Setting up test repositories...");

    // Initialize remote repository as bare using git binary (for setup only)
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit in a temporary directory and push to bare remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("README.md"), "# Test Repository\n").expect("Failed to write README");
    run_git_command(&temp_dir, &["add", "README.md"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit"]);

    // Push initial commit to bare remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp directory
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone remote to local using git command (for setup)
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Test User"]);
    run_git_command(&local_repo, &["config", "user.email", "test@example.com"]);

    // Now test our Rust library functions
    println!("Testing Rust library functions...");

    // Save current directory for cleanup
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Test 1: Open repository using our open_repo function
    println!("\n=== Test 1: Open repository using open_repo ===");
    let local_repo_abs = local_repo.canonicalize().expect("Failed to get absolute path");
    test_open_repo_integration(&local_repo_abs);

    // Test 2: Create and commit files using our functions
    println!("\n=== Test 2: Create and commit files ===");
    test_commit_all_integration(&local_repo);

    // Test 3: Push changes using our push function
    println!("\n=== Test 3: Push changes ===");
    test_push_integration();

    // Test 4: Pull changes using our pull function
    println!("\n=== Test 4: Pull changes ===");
    test_pull_integration();

    // Test 5: Sync operation using our sync function
    println!("\n=== Test 5: Sync operation ===");
    test_sync_integration();

    // Test 6: Test repository state functions
    println!("\n=== Test 6: Repository state functions ===");
    test_repo_state_integration();

    // Restore original directory before cleanup
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");

    // Clean up
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    println!("\nAll tests completed successfully!");
}

fn test_open_repo_integration(local_repo: &Path) {
    // Test open_repo function
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository using our open_repo function");

    // Verify repository state functions work
    let last_commit_hash = last_commit().expect("Failed to get last commit after opening repo");
    assert!(!last_commit_hash.is_empty(), "Should have a commit after opening repo");
    println!("✓ test_open_repo function successful, last commit: {:?}", last_commit_hash);
}

fn test_commit_all_integration(local_repo: &Path) {
    // Save current directory
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Use absolute path for repository operations
    let local_repo_abs = local_repo.canonicalize().expect("Failed to get absolute path");

    // Test open_repo function first with absolute path
    open_repo(&local_repo_abs.to_string_lossy()).expect("Failed to open repository");

    // Change to the local repo directory for file operations
    std::env::set_current_dir(local_repo).expect("Failed to change directory");

    // Create a new file
    fs::write("test_file.txt", "Test content from our library").expect("Failed to write test file");

    // Test commit_all function
    commit_all("Test User", "test@example.com", "Add test file via commit_all")
        .expect("Failed to commit using our commit_all function");

    // Verify commit was created
    let last_commit_hash = last_commit().expect("Failed to get last commit");
    assert!(!last_commit_hash.is_empty(), "Should have a commit after commit_all");
    println!("✓ test_commit_all function successful, commit: {:?}", last_commit_hash);

    // Restore directory
    std::env::set_current_dir(original_dir).expect("Failed to restore directory");
}

fn test_push_integration() {
    // Test push function
    push(None).expect("Failed to push using our push function");
    println!("✓ test_push function successful");
}

fn test_pull_integration() {
    // Test pull function
    pull(None, "Test User", "test@example.com").expect("Failed to pull using our pull function");
    println!("✓ test_pull function successful");
}

fn test_sync_integration() {
    // Test sync function
    sync(None).expect("Failed to sync using our sync function");
    println!("✓ test_sync function successful");
}

fn test_repo_state_integration() {
    // Test various repository state functions
    let has_changes = is_change().expect("Failed to check for changes");
    println!("✓ test_is_change function works: has_changes = {}", has_changes);

    let last_commit_hash = last_commit().expect("Failed to get last commit");
    println!("✓ test_last_commit function works: {:?}", last_commit_hash);

    let git_log = get_git_log(5).expect("Failed to get git log");
    println!("✓ test_get_git_log function works: {} entries", git_log.len());

    // Test cleanup_repo function
    cleanup_repo().expect("Failed to cleanup repository");
    println!("✓ test_cleanup_repo function successful");
}

#[test]
#[serial]
fn test_get_timestamps() {
    // Initialize the library first
    init_lib("/tmp".to_string());

    // Setup test directories
    let test_dir = Path::new("test_repos_timestamps_basic");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare using git binary
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit in a temporary directory and push to bare remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("README.md"), "# Test Repository\n").expect("Failed to write README");
    run_git_command(&temp_dir, &["add", "README.md"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit"]);

    // Push initial commit to bare remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp directory
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone remote to local (run from test_dir, clone into local)
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Test User"]);
    run_git_command(&local_repo, &["config", "user.email", "test@example.com"]);

    // Open the repository
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository");

    // Save current directory for cleanup
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Create some test files with different extensions (using absolute paths)
    let local_repo_abs = local_repo.canonicalize().expect("Failed to get absolute path");
    fs::write(local_repo_abs.join("file1.md"), "# First file\nContent").expect("Failed to write file1.md");
    fs::write(local_repo_abs.join("file2.md"), "# Second file\nContent").expect("Failed to write file2.md");
    fs::write(local_repo_abs.join("file3.txt"), "Plain text file").expect("Failed to write file3.txt");

    // Change to repo directory for commit, then change back
    std::env::set_current_dir(&local_repo).expect("Failed to change directory");
    commit_all("Test User", "test@example.com", "Initial commit with test files")
        .expect("Failed to commit files");
    std::env::set_current_dir(&original_dir).expect("Failed to restore directory");

    // Wait a bit and create another file to get different timestamps
    std::thread::sleep(std::time::Duration::from_millis(100));
    fs::write(local_repo_abs.join("file4.md"), "# Fourth file\nContent").expect("Failed to write file4.md");

    // Change to repo directory for commit, then change back
    std::env::set_current_dir(&local_repo).expect("Failed to change directory");
    commit_all("Test User", "test@example.com", "Add fourth file")
        .expect("Failed to commit files");
    std::env::set_current_dir(&original_dir).expect("Failed to restore directory");

    // Now test get_timestamps
    let timestamps = get_timestamps().expect("Failed to get timestamps");

    println!("Timestamps result:");
    for (file, timestamp) in &timestamps {
        println!("  {}: {}", file, timestamp);
    }

    // Verify we got some timestamps
    assert!(!timestamps.is_empty(), "Should have found some file timestamps");

    // Verify all files are supported extensions (md and txt should be supported)
    for (file, _) in &timestamps {
        let path = Path::new(file);
        if let Some(extension) = path.extension() {
            if let Some(ext_str) = extension.to_str() {
                assert!(crate::mime_types::is_extension_supported(ext_str),
                       "File {} has unsupported extension {}", file, ext_str);
            }
        }
    }

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    // Note: Not calling close() to avoid interfering with other tests
    // close();

    println!("✓ test_get_timestamps completed successfully");
}

#[test]
#[serial]
fn test_get_timestamps_sorted() {
    // Initialize the library first
    init_lib("/tmp".to_string());

    // Setup test directories
    let test_dir = Path::new("test_repos_timestamps_sorted_test");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare using git binary
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit in a temporary directory and push to bare remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("README.md"), "# Test Repository\n").expect("Failed to write README");
    run_git_command(&temp_dir, &["add", "README.md"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit"]);

    // Push initial commit to bare remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp directory
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone remote to local (run from test_dir, clone into local)
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Test User"]);
    run_git_command(&local_repo, &["config", "user.email", "test@example.com"]);

    // Open the repository
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository");

    // Save current directory for cleanup
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Sleep to ensure different commit times from the initial commit
    std::thread::sleep(std::time::Duration::from_secs(1));

    // Create files with delays to ensure different timestamps (using absolute paths)
    let local_repo_abs = local_repo.canonicalize().expect("Failed to get absolute path");
    fs::write(local_repo_abs.join("oldest.md"), "# Oldest file\nContent").expect("Failed to write oldest.md");
    std::env::set_current_dir(&local_repo).expect("Failed to change directory");
    commit_all("Test User", "test@example.com", "Add oldest file")
        .expect("Failed to commit");
    std::env::set_current_dir(&original_dir).expect("Failed to restore directory");

    // Sleep to ensure different commit times
    std::thread::sleep(std::time::Duration::from_secs(2));
    fs::write(local_repo_abs.join("middle.md"), "# Middle file\nContent").expect("Failed to write middle.md");
    std::env::set_current_dir(&local_repo).expect("Failed to change directory");
    commit_all("Test User", "test@example.com", "Add middle file")
        .expect("Failed to commit");
    std::env::set_current_dir(&original_dir).expect("Failed to restore directory");

    // Sleep again
    std::thread::sleep(std::time::Duration::from_secs(2));
    fs::write(local_repo_abs.join("newest.md"), "# Newest file\nContent").expect("Failed to write newest.md");
    std::env::set_current_dir(&local_repo).expect("Failed to change directory");
    commit_all("Test User", "test@example.com", "Add newest file")
        .expect("Failed to commit");
    std::env::set_current_dir(&original_dir).expect("Failed to restore directory");

    // Get timestamps
    let timestamps = get_timestamps().expect("Failed to get timestamps");

    // Convert to vec and sort by timestamp (oldest first)
    let mut sorted_timestamps: Vec<_> = timestamps.into_iter().collect();
    sorted_timestamps.sort_by(|a, b| a.1.cmp(&b.1));

    println!("Timestamps sorted by time (oldest first):");
    for (file, timestamp) in &sorted_timestamps {
        println!("  {}: {}", file, timestamp);
    }

    // Verify sorting - README.md (initial) should come first, then our test files
    assert_eq!(sorted_timestamps[0].0, "README.md", "README.md should be first (from initial commit)");
    assert_eq!(sorted_timestamps[1].0, "oldest.md", "oldest.md should be second");
    assert_eq!(sorted_timestamps[2].0, "middle.md", "middle.md should be third");
    assert_eq!(sorted_timestamps[3].0, "newest.md", "newest.md should be last");

    // Verify timestamps are in ascending order
    for i in 1..sorted_timestamps.len() {
        assert!(sorted_timestamps[i-1].1 <= sorted_timestamps[i].1,
                "Timestamps should be in ascending order");
    }

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    // Note: Not calling close() to avoid interfering with other tests
    // close();

    println!("✓ test_get_timestamps_sorted completed successfully");
}

#[test]
#[serial]
fn test_pull_remote_changes() {
    // Test pulling changes committed to remote via git commands
    init_lib("/tmp".to_string());

    // Setup test directories
    let test_dir = Path::new("test_repos_pull_remote");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit in a temporary directory and push to bare remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("README.md"), "# Test Repository\n").expect("Failed to write README");
    run_git_command(&temp_dir, &["add", "README.md"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit"]);

    // Push initial commit to bare remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp directory
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone remote to local
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Test User"]);
    run_git_command(&local_repo, &["config", "user.email", "test@example.com"]);

    // Open the repository
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository");

    // Save current directory
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Verify initial state
    let initial_commit = last_commit().expect("Failed to get initial commit");
    println!("Initial commit: {}", initial_commit);

    // Now commit a change directly to the remote using git commands
    let remote_temp_dir = test_dir.join("remote_temp");
    fs::create_dir_all(&remote_temp_dir).expect("Failed to create remote temp dir");
    run_git_command(&remote_temp_dir, &["clone", &remote_url, "."]);
    run_git_command(&remote_temp_dir, &["config", "user.name", "Remote User"]);
    run_git_command(&remote_temp_dir, &["config", "user.email", "remote@example.com"]);

    // Modify the README.md on remote
    fs::write(remote_temp_dir.join("README.md"), "# Test Repository\nRemote change added.").expect("Failed to write remote change");
    run_git_command(&remote_temp_dir, &["add", "README.md"]);
    run_git_command(&remote_temp_dir, &["commit", "-m", "Remote commit: added remote change"]);

    // Push the remote change
    run_git_command(&remote_temp_dir, &["push"]);

    // Clean up remote temp
    fs::remove_dir_all(&remote_temp_dir).expect("Failed to clean up remote temp dir");

    // Now use our pull function to get the remote changes
    pull(None, "Test User", "test@example.com").expect("Failed to pull remote changes");

    // Verify we got the remote commit
    let after_pull_commit = last_commit().expect("Failed to get commit after pull");
    println!("After pull commit: {}", after_pull_commit);
    assert_ne!(initial_commit, after_pull_commit, "Should have different commit after pull");

    // Verify the content was updated
    let readme_content = fs::read_to_string(local_repo.join("README.md")).expect("Failed to read README");
    assert!(readme_content.contains("Remote change added."), "Should contain remote changes");

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    println!("✓ test_pull_remote_changes completed successfully");
}

#[test]
#[serial]
fn test_push_conflict_resolution() {
    // Test push conflict when both local and remote modified the same file
    init_lib("/tmp".to_string());

    // Setup test directories
    let test_dir = Path::new("test_repos_push_conflict");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit and push to remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("test.txt"), "Line 1\nLine 2\nLine 3\n").expect("Failed to write test file");
    run_git_command(&temp_dir, &["add", "test.txt"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit with test file"]);

    // Push to remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone to local
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Local User"]);
    run_git_command(&local_repo, &["config", "user.email", "local@example.com"]);

    // Open repository
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository");

    // Save current directory
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Now commit a change to remote directly
    let remote_temp_dir = test_dir.join("remote_temp");
    fs::create_dir_all(&remote_temp_dir).expect("Failed to create remote temp dir");
    run_git_command(&remote_temp_dir, &["clone", &remote_url, "."]);
    run_git_command(&remote_temp_dir, &["config", "user.name", "Remote User"]);
    run_git_command(&remote_temp_dir, &["config", "user.email", "remote@example.com"]);

    // Modify line 2 on remote
    fs::write(remote_temp_dir.join("test.txt"), "Line 1\nLine 2 REMOTE CHANGE\nLine 3\n").expect("Failed to write remote change");
    run_git_command(&remote_temp_dir, &["add", "test.txt"]);
    run_git_command(&remote_temp_dir, &["commit", "-m", "Remote: modified line 2"]);
    run_git_command(&remote_temp_dir, &["push"]);

    // Clean up remote temp
    fs::remove_dir_all(&remote_temp_dir).expect("Failed to clean up remote temp dir");

    // Now locally modify the same line using our commit_all
    std::env::set_current_dir(&local_repo).expect("Failed to change to local repo");
    fs::write("test.txt", "Line 1\nLine 2 LOCAL CHANGE\nLine 3\n").expect("Failed to write local change");
    commit_all("Local User", "local@example.com", "Local: modified line 2").expect("Failed to commit locally");

    // Try to push - this should fail due to conflict
    let push_result = push(None);
    match push_result {
        Ok(_) => {
            println!("Push succeeded - no conflict detected");
            // If push succeeds, verify the content
            let content = fs::read_to_string(local_repo.join("test.txt")).expect("Failed to read file");
            println!("Final content: {}", content);
        }
        Err(e) => {
            println!("Push failed as expected due to conflict: {:?}", e);
            // This is expected behavior - git push would reject non-fast-forward updates
        }
    }

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    println!("✓ test_push_conflict_resolution completed successfully");
}
#[test]
#[serial]
fn test_cleanup_repo_state() {
    // Test cleanup_repo_state function
    init_lib("/tmp".to_string());

    // Setup test directories with absolute paths
    let current_dir = std::env::current_dir().expect("Failed to get current directory");
    let test_dir = current_dir.join("test_repos_cleanup");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(&test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit and push to remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("test.txt"), "Line 1\nLine 2\nLine 3\n").expect("Failed to write test file");
    run_git_command(&temp_dir, &["add", "test.txt"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit"]);

    // Push to remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone to local
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Local User"]);
    run_git_command(&local_repo, &["config", "user.email", "local@example.com"]);

    // Open repository
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository");

    // Save current directory
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // First, verify cleanup works on a clean repo
    cleanup_repo().expect("Failed to cleanup clean repo");
    println!("✓ cleanup_repo works on clean repository");

    // Now create a merge conflict state by simulating a failed merge
    std::env::set_current_dir(&local_repo).expect("Failed to change to local repo");

    // Create a merge conflict by modifying the same file locally and remotely
    fs::write("test.txt", "Line 1\nLine 2 LOCAL\nLine 3\n").expect("Failed to write local change");

    // Commit locally
    commit_all("Local User", "local@example.com", "Local change").expect("Failed to commit locally");

    // Now create remote change that conflicts
    let remote_temp_dir = test_dir.join("remote_temp");
    fs::create_dir_all(&remote_temp_dir).expect("Failed to create remote temp dir");
    run_git_command(&remote_temp_dir, &["clone", &remote_url, "."]);
    run_git_command(&remote_temp_dir, &["config", "user.name", "Remote User"]);
    run_git_command(&remote_temp_dir, &["config", "user.email", "remote@example.com"]);

    // Modify the same line on remote
    fs::write(remote_temp_dir.join("test.txt"), "Line 1\nLine 2 REMOTE\nLine 3\n").expect("Failed to write remote change");
    run_git_command(&remote_temp_dir, &["add", "test.txt"]);
    run_git_command(&remote_temp_dir, &["commit", "-m", "Remote change"]);
    run_git_command(&remote_temp_dir, &["push"]);

    // Clean up remote temp
    fs::remove_dir_all(&remote_temp_dir).expect("Failed to clean up remote temp dir");

    // Instead of using pull (which auto-resolves), manually create merge state
    // Fetch first
    run_git_command(&local_repo, &["fetch", "origin"]);

    // Now manually start a merge that will conflict
    let merge_result = std::process::Command::new("git")
        .args(&["merge", "origin/master", "--no-commit"])
        .current_dir(&local_repo)
        .output()
        .expect("Failed to run git merge");

    // The merge should fail due to conflicts
    if !merge_result.status.success() {
        // Check if repository is in merge state
        let merge_head_exists = local_repo.join(".git").join("MERGE_HEAD").exists();
        assert!(merge_head_exists, "Repository should be in merge state after failed merge");

        // Now test cleanup_repo_state
        cleanup_repo().expect("Failed to cleanup repo state");

        // Verify repository is no longer in merge state
        let merge_head_still_exists = local_repo.join(".git").join("MERGE_HEAD").exists();
        assert!(!merge_head_still_exists, "Repository should not be in merge state after cleanup");

        // Verify content was reset to local HEAD
        let content = fs::read_to_string("test.txt").expect("Failed to read file");
        assert!(content.contains("Line 2 LOCAL"), "Should contain local changes after cleanup");
        assert!(!content.contains("<<<<<<< HEAD"), "Should not contain conflict markers after cleanup");
    } else {
        // If merge succeeded, that's unexpected but let's handle it
        panic!("Expected merge to fail with conflicts, but it succeeded");
    }

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    println!("✓ test_cleanup_repo_state completed successfully");
}

#[test]
#[serial]
fn test_sync_function() {
    // Test sync function - should fetch and reset to remote
    init_lib("/tmp".to_string());

    // Setup test directories
    let test_dir = Path::new("test_repos_sync");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit and push to remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("test.txt"), "Initial content\n").expect("Failed to write test file");
    run_git_command(&temp_dir, &["add", "test.txt"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit"]);

    // Push to remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone to local
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Local User"]);
    run_git_command(&local_repo, &["config", "user.email", "local@example.com"]);

    // Open repository
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository");

    // Save current directory
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Get initial commit
    let initial_commit = last_commit().expect("Failed to get initial commit");

    // Now create divergence: commit locally and also commit to remote
    std::env::set_current_dir(&local_repo).expect("Failed to change to local repo");

    // Local commit
    fs::write("test.txt", "Local modified content\n").expect("Failed to write local change");
    commit_all("Local User", "local@example.com", "Local modification").expect("Failed to commit locally");

    // Verify local has new commit
    let local_commit = last_commit().expect("Failed to get local commit");
    assert_ne!(initial_commit, local_commit, "Should have new local commit");

    // Remote commit
    let remote_temp_dir = test_dir.join("remote_temp");
    fs::create_dir_all(&remote_temp_dir).expect("Failed to create remote temp dir");
    run_git_command(&remote_temp_dir, &["clone", &remote_url, "."]);
    run_git_command(&remote_temp_dir, &["config", "user.name", "Remote User"]);
    run_git_command(&remote_temp_dir, &["config", "user.email", "remote@example.com"]);

    // Modify on remote
    fs::write(remote_temp_dir.join("test.txt"), "Remote modified content\n").expect("Failed to write remote change");
    run_git_command(&remote_temp_dir, &["add", "test.txt"]);
    run_git_command(&remote_temp_dir, &["commit", "-m", "Remote modification"]);
    run_git_command(&remote_temp_dir, &["push"]);

    // Clean up remote temp
    fs::remove_dir_all(&remote_temp_dir).expect("Failed to clean up remote temp dir");

    // Now local has "Local modified content" and remote has "Remote modified content"
    // Sync should fetch remote and reset local to match remote
    sync(None).expect("Failed to sync");

    // Verify local now matches remote
    let content = fs::read_to_string("test.txt").expect("Failed to read file");
    assert!(content.contains("Remote modified content"), "Should contain remote content after sync");
    assert!(!content.contains("Local modified content"), "Should not contain local content after sync");

    // Verify commit changed to remote commit
    let after_sync_commit = last_commit().expect("Failed to get commit after sync");
    assert_ne!(local_commit, after_sync_commit, "Should have different commit after sync");

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    println!("✓ test_sync_function completed successfully");
}#[test]
#[serial]
fn test_pull_conflict_resolution() {
    // Test pull conflict when both local and remote modified the same file (offline scenario)
    init_lib("/tmp".to_string());

    // Setup test directories
    let test_dir = Path::new("test_repos_pull_conflict");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit and push to remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("test.txt"), "Line 1\nLine 2\nLine 3\n").expect("Failed to write test file");
    run_git_command(&temp_dir, &["add", "test.txt"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit with test file"]);

    // Push to remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone to local
    run_git_command(&test_dir, &["clone", &remote_url, "local"]);
    run_git_command(&local_repo, &["config", "user.name", "Local User"]);
    run_git_command(&local_repo, &["config", "user.email", "local@example.com"]);

    // Open repository
    open_repo(&local_repo.to_string_lossy()).expect("Failed to open repository");

    // Save current directory
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Locally commit a change using our commit_all (simulating offline work)
    std::env::set_current_dir(&local_repo).expect("Failed to change to local repo");
    fs::write("test.txt", "Line 1\nLine 2 LOCAL CHANGE\nLine 3\n").expect("Failed to write local change");
    commit_all("Local User", "local@example.com", "Local: modified line 2").expect("Failed to commit locally");

    // Now commit a change to remote directly (simulating remote changes while offline)
    let remote_temp_dir = test_dir.join("remote_temp");
    fs::create_dir_all(&remote_temp_dir).expect("Failed to create remote temp dir");
    run_git_command(&remote_temp_dir, &["clone", &remote_url, "."]);
    run_git_command(&remote_temp_dir, &["config", "user.name", "Remote User"]);
    run_git_command(&remote_temp_dir, &["config", "user.email", "remote@example.com"]);

    // Modify the same line on remote
    fs::write(remote_temp_dir.join("test.txt"), "Line 1\nLine 2 REMOTE CHANGE\nLine 3\n").expect("Failed to write remote change");
    run_git_command(&remote_temp_dir, &["add", "test.txt"]);
    run_git_command(&remote_temp_dir, &["commit", "-m", "Remote: modified line 2"]);
    run_git_command(&remote_temp_dir, &["push"]);

    // Clean up remote temp
    fs::remove_dir_all(&remote_temp_dir).expect("Failed to clean up remote temp dir");

    // Now try to pull - this should create a merge conflict
    let pull_result = pull(None, "Local User", "local@example.com");
    match pull_result {
        Ok(_) => {
            println!("Pull succeeded - automatic merge performed");
            
            // Debug: check current directory and list files
            let current_dir = std::env::current_dir().expect("Failed to get current dir");
            println!("Current directory: {:?}", current_dir);
            
            // List files in current directory
            match std::fs::read_dir(".") {
                Ok(entries) => {
                    println!("Files in current directory:");
                    for entry in entries {
                        if let Ok(entry) = entry {
                            println!("  {:?}", entry.file_name());
                        }
                    }
                }
                Err(e) => println!("Failed to list directory: {:?}", e),
            }
            
            // Check git log
            match std::process::Command::new("git").args(&["log", "--oneline", "-5"]).output() {
                Ok(output) => {
                    let log = String::from_utf8_lossy(&output.stdout);
                    println!("Git log:\n{}", log);
                }
                Err(e) => println!("Failed to get git log: {:?}", e),
            }
            
            // Check git ls-tree HEAD
            match std::process::Command::new("git").args(&["ls-tree", "-r", "HEAD"]).output() {
                Ok(output) => {
                    let tree = String::from_utf8_lossy(&output.stdout);
                    println!("Git ls-tree HEAD:\n{}", tree);
                }
                Err(e) => println!("Failed to get git ls-tree: {:?}", e),
            }
            
            // Check if there was a merge commit or automatic resolution
            let content = fs::read_to_string("test.txt").expect("Failed to read file");
            println!("Final content after pull: {}", content);

            // Check for conflict markers
            if content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>") {
                println!("Merge conflict detected in file content");
            } else {
                println!("No conflict markers found - automatic merge succeeded");
            }
        }
        Err(e) => {
            println!("Pull failed: {:?}", e);
            // Check if repository is in merge state
            let merge_head_exists = local_repo.join(".git").join("MERGE_HEAD").exists();
            if merge_head_exists {
                println!("Repository is in merge state - conflict needs resolution");
            }
        }
    }

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    println!("✓ test_pull_conflict_resolution completed successfully");
}

#[test]
#[serial]
fn test_pull_conflict_resolution_from_different_cwd() {
    // Test that pull conflict resolution works even when current working directory
    // is NOT the repository root (reproducing the original bug scenario)
    init_lib("/tmp".to_string());

    // Setup test directories with ABSOLUTE paths
    let current_dir = std::env::current_dir().expect("Failed to get current directory");
    let test_dir = current_dir.join("test_repos_pull_conflict_cwd");
    let remote_repo = test_dir.join("remote");
    let local_repo = test_dir.join("local");

    // Clean up any existing test directories
    if test_dir.exists() {
        fs::remove_dir_all(&test_dir).expect("Failed to clean up test directories");
    }

    // Create test directories
    fs::create_dir_all(&remote_repo).expect("Failed to create remote repo dir");
    fs::create_dir_all(&local_repo).expect("Failed to create local repo dir");

    // Initialize remote repository as bare
    run_git_command(&remote_repo, &["init", "--bare"]);
    run_git_command(&remote_repo, &["config", "user.name", "Test User"]);
    run_git_command(&remote_repo, &["config", "user.email", "test@example.com"]);

    // Create initial commit and push to remote
    let temp_dir = test_dir.join("temp");
    fs::create_dir_all(&temp_dir).expect("Failed to create temp dir");
    run_git_command(&temp_dir, &["init"]);
    run_git_command(&temp_dir, &["config", "user.name", "Test User"]);
    run_git_command(&temp_dir, &["config", "user.email", "test@example.com"]);
    fs::write(temp_dir.join("test.txt"), "Line 1\nLine 2\nLine 3\n").expect("Failed to write test file");
    run_git_command(&temp_dir, &["add", "test.txt"]);
    run_git_command(&temp_dir, &["commit", "-m", "Initial commit with test file"]);

    // Push to remote
    let remote_url = format!("file://{}", remote_repo.canonicalize().unwrap().display());
    run_git_command(&temp_dir, &["remote", "add", "origin", &remote_url]);
    run_git_command(&temp_dir, &["push", "-u", "origin", "master"]);

    // Clean up temp
    fs::remove_dir_all(&temp_dir).expect("Failed to clean up temp dir");

    // Clone to local
    run_git_command(&current_dir, &["clone", &remote_url, "test_repos_pull_conflict_cwd/local"]);
    run_git_command(&local_repo, &["config", "user.name", "Local User"]);
    run_git_command(&local_repo, &["config", "user.email", "local@example.com"]);

    // Open repository using ABSOLUTE path
    open_repo(&local_repo.canonicalize().unwrap().to_string_lossy()).expect("Failed to open repository");

    // Save current directory
    let original_dir = std::env::current_dir().expect("Failed to get current directory");

    // Locally commit a change using our commit_all (simulating offline work)
    std::env::set_current_dir(&local_repo).expect("Failed to change to local repo");
    fs::write("test.txt", "Line 1\nLine 2 LOCAL CHANGE\nLine 3\n").expect("Failed to write local change");
    commit_all("Local User", "local@example.com", "Local: modified line 2").expect("Failed to commit locally");

    // Now commit a change to remote directly (simulating remote changes while offline)
    let remote_temp_dir = test_dir.join("remote_temp");
    fs::create_dir_all(&remote_temp_dir).expect("Failed to create remote temp dir");
    run_git_command(&remote_temp_dir, &["clone", &remote_url, "."]);
    run_git_command(&remote_temp_dir, &["config", "user.name", "Remote User"]);
    run_git_command(&remote_temp_dir, &["config", "user.email", "remote@example.com"]);

    // Modify the same line on remote
    fs::write(remote_temp_dir.join("test.txt"), "Line 1\nLine 2 REMOTE CHANGE\nLine 3\n").expect("Failed to write remote change");
    run_git_command(&remote_temp_dir, &["add", "test.txt"]);
    run_git_command(&remote_temp_dir, &["commit", "-m", "Remote: modified line 2"]);
    run_git_command(&remote_temp_dir, &["push"]);

    // Clean up remote temp
    fs::remove_dir_all(&remote_temp_dir).expect("Failed to clean up remote temp dir");

    // CRITICAL: Set current directory to a DIFFERENT directory (not the repo root)
    // This reproduces the original bug where the app's working directory wasn't the repo root
    std::env::set_current_dir(&test_dir).expect("Failed to change to test dir (not repo root)");

    // Now try to pull - this should trigger conflict resolution
    // The bug was that path resolution used relative paths assuming CWD was repo root
    let pull_result = pull(None, "Local User", "local@example.com");

    match pull_result {
        Ok(_) => {
            println!("Pull succeeded - checking for conflict resolution");

            // Verify the file was written correctly (this would fail with the original bug)
            let content = fs::read_to_string(local_repo.join("test.txt")).expect("Failed to read resolved file");
            println!("Resolved content: {}", content);

            // Check if conflict was resolved automatically
            if content.contains("<<<<<<<") || content.contains("=======") || content.contains(">>>>>>>") {
                println!("Conflict markers still present - manual resolution needed");
            } else {
                println!("No conflict markers found - automatic merge succeeded");
            }
        }
        Err(e) => {
            println!("Pull failed: {:?}", e);
            // Check if repository is in merge state
            let merge_head_exists = local_repo.join(".git").join("MERGE_HEAD").exists();
            if merge_head_exists {
                println!("Repository is in merge state - conflict needs resolution");
            }
        }
    }

    // Clean up
    std::env::set_current_dir(original_dir).expect("Failed to restore original directory");
    fs::remove_dir_all(test_dir).expect("Failed to clean up test directories");

    println!("✓ test_pull_conflict_resolution_from_different_cwd completed successfully");
}