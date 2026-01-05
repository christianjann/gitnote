use std::{
    collections::HashMap,
    fs,
    path::Path,
    str::FromStr,
    sync::{LazyLock, Mutex, OnceLock},
};

use chrono::{DateTime, Local};

use git2::{
    CertificateCheckStatus, FetchOptions, IndexAddOption, Progress, PushOptions, RemoteCallbacks,
    Repository, Signature, StatusOptions, TreeWalkMode, TreeWalkResult,
};

use crate::{Cred, Error, ProgressCB, mime_types::is_extension_supported};

mod merge;

#[cfg(test)]
mod test;

const REMOTE: &str = "origin";

static REPO: LazyLock<Mutex<Option<Repository>>> = LazyLock::new(|| Mutex::new(None));

// https://github.com/libgit2/libgit2/pull/7056
static HOME_PATH: OnceLock<String> = OnceLock::new();

fn apply_ssh_workaround(clone: bool) {
    let home = HOME_PATH.get().unwrap();

    if clone {
        unsafe {
            std::env::set_var("HOME", home);
        }
    } else {
        let c_path = std::ffi::CString::from_str(home).expect("CString::new failed");

        unsafe {
            libgit2_sys::git_libgit2_opts(
                libgit2_sys::GIT_OPT_SET_HOMEDIR as std::ffi::c_int,
                c_path.as_ptr(),
            )
        };
    }

    if let Err(e) = std::fs::create_dir_all(format!("{home}/.ssh")) {
        error!("{e}");
    }
    if let Err(e) = std::fs::File::create(format!("{home}/.ssh/known_hosts")) {
        error!("{e}");
    }
}

fn cleanup_repo_state(repo: &Repository) -> Result<(), Error> {
    info!("Checking and cleaning up repository state...");

    let mut cleaned_something = false;

    // Check for ongoing merge state
    if repo.find_reference("MERGE_HEAD").is_ok() {
        warn!("Repository is in merge state, cleaning up merge state...");
        // Remove merge state files
        let _ = repo
            .find_reference("MERGE_HEAD")
            .and_then(|mut r| r.delete());
        let _ = repo
            .find_reference("MERGE_MSG")
            .and_then(|mut r| r.delete());
        let _ = repo
            .find_reference("MERGE_MODE")
            .and_then(|mut r| r.delete());
        cleaned_something = true;
    }

    // Check for ongoing rebase state
    if repo.find_reference("REBASE_HEAD").is_ok() {
        warn!("Repository is in rebase state, cleaning up rebase state...");
        // Remove rebase state files
        let _ = repo
            .find_reference("REBASE_HEAD")
            .and_then(|mut r| r.delete());
        let _ = repo
            .find_reference("REBASE_SEQ")
            .and_then(|mut r| r.delete());
        cleaned_something = true;
    }

    // Check for cherry-pick state
    if repo.find_reference("CHERRY_PICK_HEAD").is_ok() {
        warn!("Repository is in cherry-pick state, cleaning up cherry-pick state...");
        let _ = repo
            .find_reference("CHERRY_PICK_HEAD")
            .and_then(|mut r| r.delete());
        let _ = repo
            .find_reference("CHERRY_PICK_SEQ")
            .and_then(|mut r| r.delete());
        cleaned_something = true;
    }

    // Check for revert state
    if repo.find_reference("REVERT_HEAD").is_ok() {
        warn!("Repository is in revert state, cleaning up revert state...");
        let _ = repo
            .find_reference("REVERT_HEAD")
            .and_then(|mut r| r.delete());
        cleaned_something = true;
    }

    // Check if index has conflicts
    if let Ok(index) = repo.index() {
        if index.has_conflicts() {
            warn!("Index has conflicts, resetting to HEAD...");
            if let (Ok(_head), Ok(head_commit)) = (
                repo.head(),
                repo.head()
                    .and_then(|h| repo.find_commit(h.target().unwrap())),
            ) {
                let _ = repo.reset(head_commit.as_object(), git2::ResetType::Hard, None);
                cleaned_something = true;
            }
        }
    }

    // Check for uncommitted changes and reset if needed
    let mut status_opts = StatusOptions::new();
    status_opts.include_untracked(false);
    if let Ok(statuses) = repo.statuses(Some(&mut status_opts)) {
        if statuses.iter().any(|s| s.status() != git2::Status::CURRENT) {
            warn!("Repository has uncommitted changes, resetting to HEAD...");
            if let (Ok(_head), Ok(head_commit)) = (
                repo.head(),
                repo.head()
                    .and_then(|h| repo.find_commit(h.target().unwrap())),
            ) {
                let _ = repo.reset(head_commit.as_object(), git2::ResetType::Hard, None);
                cleaned_something = true;
            }
        }
    }

    if cleaned_something {
        info!("Repository state was cleaned up");
    } else {
        info!("Repository state was already clean");
    }
    Ok(())
}

pub fn init_lib(home_path: String) {
    info!("home_path: {home_path}");
    let _ = HOME_PATH.set(home_path.clone());

    unsafe {
        std::env::set_var("HOME", &home_path);
    }

    let git_config_path = Path::new(&home_path).join(".gitconfig");

    let git_config_content = "[safe]\n\tdirectory = *";

    match fs::exists(&git_config_path) {
        Ok(true) => {}
        Ok(false) => {
            if let Err(e) = fs::create_dir_all(git_config_path.parent().unwrap()) {
                error!("gitconfig: {e}");
            }

            if let Err(e) = fs::write(&git_config_path, git_config_content) {
                error!("gitconfig: {e}");
            } else {
                debug!("successfully written the gitconfig file")
            }
        }
        Err(e) => {
            error!("gitconfig: {e}");
        }
    }

    unsafe {
        libgit2_sys::git_libgit2_opts(
            libgit2_sys::GIT_OPT_SET_SERVER_CONNECT_TIMEOUT as std::ffi::c_int,
            7000,
        );
        libgit2_sys::git_libgit2_opts(
            libgit2_sys::GIT_OPT_SET_SERVER_TIMEOUT as std::ffi::c_int,
            7000,
        );
    };
}

pub fn create_repo(repo_path: &str) -> Result<(), Error> {
    let repo = Repository::init(repo_path).map_err(|e| Error::git2(e, "Repository::init"))?;

    REPO.lock().unwrap().replace(repo);

    Ok(())
}

pub fn open_repo(repo_path: &str) -> Result<(), Error> {
    let repo = Repository::open(repo_path).map_err(|e| Error::git2(e, "Repository::open"))?;

    // Automatically clean up any broken repository state when opening
    if let Err(e) = cleanup_repo_state(&repo) {
        warn!("Failed to cleanup repository state on open: {}", e);
        // Continue anyway - the repo is still opened
    }

    REPO.lock().unwrap().replace(repo);

    Ok(())
}

fn current_branch(repo: &Repository) -> Result<String, Error> {
    let head = repo.head().map_err(|e| Error::git2(e, "head"))?;

    if head.is_branch()
        && let Some(name) = head.shorthand()
    {
        return Ok(name.to_string());
    }

    // Detached HEAD or not a branch
    Err(Error::git2(
        git2::Error::from_str("unable to determine default branch"),
        "",
    ))
}

fn credential_helper(cred: &Cred) -> Result<git2::Cred, git2::Error> {
    match cred {
        Cred::UserPassPlainText { username, password } => {
            git2::Cred::userpass_plaintext(username, password)
        }
        Cred::Ssh {
            username,
            private_key,
            public_key,
            passphrase,
        } => git2::Cred::ssh_key_from_memory(
            username,
            Some(public_key),
            private_key,
            passphrase.as_deref(),
        ),
    }
}

pub fn clone_repo(
    repo_path: &str,
    remote_url: &str,
    cred: Option<Cred>,
    mut cb: ProgressCB,
) -> Result<(), Error> {
    apply_ssh_workaround(true);
    let mut callbacks = RemoteCallbacks::new();

    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));

    if let Some(cred) = cred {
        callbacks
            .credentials(move |_url, _username_from_url, _allowed_types| credential_helper(&cred));
    }

    callbacks.transfer_progress(|stats: Progress| {
        let progress = stats.indexed_objects() as f32 / stats.total_objects() as f32 * 100.;

        cb.progress(progress as i32)
    });

    let mut fetch_options = FetchOptions::new();
    fetch_options.remote_callbacks(callbacks);

    let mut builder = git2::build::RepoBuilder::new();

    let repo = match builder
        .fetch_options(fetch_options)
        .clone(remote_url, std::path::Path::new(&repo_path))
    {
        Ok(repo) => repo,
        Err(e) => {
            // Provide specific guidance for authentication errors on common Git hosting services
            if remote_url.starts_with("http://") || remote_url.starts_with("https://") {
                if remote_url.contains("github.com") {
                    error!(
                        "GitHub clone failed - ensure you're using a Personal Access Token, not your password"
                    );
                    return Err(Error::git2(
                        e,
                        "GitHub clone failed - check credentials (use Personal Access Token)",
                    ));
                } else if remote_url.contains("bitbucket.org") {
                    error!(
                        "Bitbucket clone failed - ensure you're using an App Password, not your password"
                    );
                    return Err(Error::git2(
                        e,
                        "Bitbucket clone failed - check credentials (use App Password)",
                    ));
                } else if remote_url.contains("gitlab.com") || remote_url.contains("gitlab.") {
                    error!(
                        "GitLab clone failed - ensure you're using a Personal Access Token, not your password"
                    );
                    return Err(Error::git2(
                        e,
                        "GitLab clone failed - check credentials (use Personal Access Token)",
                    ));
                } else if remote_url.contains("azure.com")
                    || remote_url.contains("visualstudio.com")
                {
                    error!(
                        "Azure DevOps clone failed - ensure you're using a Personal Access Token"
                    );
                    return Err(Error::git2(
                        e,
                        "Azure DevOps clone failed - check credentials (use Personal Access Token)",
                    ));
                } else if remote_url.contains("codecommit.") {
                    error!("AWS CodeCommit clone failed - check IAM permissions and credentials");
                    return Err(Error::git2(
                        e,
                        "AWS CodeCommit clone failed - check IAM permissions",
                    ));
                } else {
                    error!("HTTP clone failed - check credentials for {}", remote_url);
                    return Err(Error::git2(e, "HTTP clone failed - check credentials"));
                }
            } else {
                return Err(Error::git2(e, "clone"));
            }
        }
    };

    // Handle empty repositories or missing main/master branch
    setup_repository_after_clone(&repo)?;

    REPO.lock().unwrap().replace(repo);

    Ok(())
}

fn setup_repository_after_clone(repo: &Repository) -> Result<(), Error> {
    // Check if repository has any commits
    let has_commits = repo.head().is_ok();

    if !has_commits {
        // Repository is empty, create initial commit and branch
        create_initial_commit_and_branch(repo)?;
    } else {
        // Repository has commits, but check if main/master branch exists
        let has_main_branch = repo.find_branch("main", git2::BranchType::Local).is_ok();
        let has_master_branch = repo.find_branch("master", git2::BranchType::Local).is_ok();

        if !has_main_branch && !has_master_branch {
            // No main or master branch, create main branch from current HEAD
            let head_commit = repo.head()?.peel_to_commit()?;
            repo.branch("main", &head_commit, false)?;
            repo.set_head("refs/heads/main")?;
        }
    }

    Ok(())
}

fn create_initial_commit_and_branch(repo: &Repository) -> Result<(), Error> {
    // Create a .gitkeep file to have something to commit
    let gitkeep_path = repo.path().parent().unwrap().join(".gitkeep");
    fs::write(&gitkeep_path, "").map_err(|e| {
        error!("Failed to write .gitkeep file: {}", e);
        git2::Error::from_str(&format!("IO error: {}", e))
    })?;

    // Add .gitkeep to index
    let mut index = repo.index().map_err(|e| Error::git2(e, "index"))?;
    index
        .add_path(Path::new(".gitkeep"))
        .map_err(|e| Error::git2(e, "add_path"))?;
    index.write().map_err(|e| Error::git2(e, "write index"))?;

    // Create tree
    let tree_oid = index
        .write_tree()
        .map_err(|e| Error::git2(e, "write_tree"))?;
    let tree = repo
        .find_tree(tree_oid)
        .map_err(|e| Error::git2(e, "find_tree"))?;

    // Create signature
    let sig = Signature::now("GitNoteCJE", "gitnote@localhost")
        .map_err(|e| Error::git2(e, "Signature::now"))?;

    // Create initial commit
    repo.commit(Some("HEAD"), &sig, &sig, "Initial commit", &tree, &[])
        .map_err(|e| Error::git2(e, "initial commit"))?;

    // Create main branch
    let head_commit = repo.head()?.peel_to_commit()?;
    match repo.branch("main", &head_commit, false) {
        Ok(_) => {}
        Err(e) if e.raw_code() == -4 => {
            // Branch already exists, update it to point to the new commit
            if let Ok(mut branch) = repo.find_branch("main", git2::BranchType::Local) {
                branch
                    .get_mut()
                    .set_target(head_commit.id(), "Update main branch to initial commit")?;
            }
        }
        Err(e) => return Err(Error::git2(e, "create main branch")),
    }
    repo.set_head("refs/heads/main")
        .map_err(|e| Error::git2(e, "set head to main"))?;

    Ok(())
}

pub fn last_commit() -> Option<String> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref()?;

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, returning None for last_commit");
        return None;
    }

    // new repo have no commit, so this function can fail
    let head = repo.refname_to_id("HEAD").ok()?;

    Some(head.to_string())
}

pub fn signature() -> Option<(String, String)> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref()?;

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, returning None for signature");
        return None;
    }

    if let Ok(signature) = repo.signature() {
        let name = signature.name().unwrap_or_default().to_string();
        let email = signature.email().unwrap_or_default().to_string();

        if !name.is_empty() || !email.is_empty() {
            return Some((name, email));
        }
    }

    let head = repo.head().ok()?;
    let commit = head.peel_to_commit().ok()?;
    let author = commit.author();

    Some((
        author.name().unwrap_or_default().to_string(),
        author.email().unwrap_or_default().to_string(),
    ))
}

pub fn commit_all(name: &str, email: &str, message: &str) -> Result<(), Error> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, cannot commit");
        return Err(Error::git2(
            git2::Error::from_str("Repository in invalid state"),
            "commit",
        ));
    }

    let mut index = repo.index().map_err(|e| Error::git2(e, "index"))?;

    index
        .add_all(["*"].iter(), IndexAddOption::DEFAULT, None)
        .map_err(|e| Error::git2(e, "add_all"))?;

    // Write index to disk
    index.write().map_err(|e| Error::git2(e, "write"))?;

    // Write tree
    let tree_oid = index
        .write_tree()
        .map_err(|e| Error::git2(e, "write_tree"))?;

    let tree = repo
        .find_tree(tree_oid)
        .map_err(|e| Error::git2(e, "find_tree"))?;

    // Get HEAD commit as parent, and Allow initial commit
    let parent_commit = repo.head().and_then(|r| r.peel_to_commit()).ok();

    let sig = Signature::now(name, email).map_err(|e| Error::git2(e, "Signature::now"))?;

    // Create commit
    match parent_commit {
        Some(ref parent) => repo.commit(Some("HEAD"), &sig, &sig, message, &tree, &[parent]),
        None => repo.commit(Some("HEAD"), &sig, &sig, message, &tree, &[]),
    }
    .map(|_| ())
    .map_err(|e| Error::git2(e, "commit"))
}

pub fn checkout_path(path: &str) -> Result<(), Error> {
    info!("checkout_path called with path: {}", path);
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // Get HEAD tree
    let head = repo.head().map_err(|e| Error::git2(e, "head"))?;
    let commit = head
        .peel_to_commit()
        .map_err(|e| Error::git2(e, "peel_to_commit"))?;
    let tree = commit.tree().map_err(|e| Error::git2(e, "tree"))?;

    info!("About to checkout tree for path: {}", path);
    // Checkout the path from HEAD
    repo.checkout_tree(
        tree.as_object(),
        Some(git2::build::CheckoutBuilder::new().path(path).force()),
    )
    .map_err(|e| Error::git2(e, "checkout_tree"))?;

    info!("checkout_path completed successfully for path: {}", path);
    Ok(())
}

pub fn push(cred: Option<Cred>) -> Result<(), Error> {
    apply_ssh_workaround(false);

    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, cannot push");
        return Err(Error::git2(
            git2::Error::from_str("Repository in invalid state"),
            "push",
        ));
    }

    let mut remote = repo
        .find_remote(REMOTE)
        .map_err(|e| Error::git2(e, "find_remote"))?;

    info!("Push: remote URL: {:?}", remote.url());
    debug!("Push: cred provided: {}", cred.is_some());

    // Check if remote URL is HTTP and no credentials provided
    if let Some(url) = remote.url() {
        if url.starts_with("http://") || url.starts_with("https://") {
            if cred.is_none() {
                error!("HTTP push attempted without credentials for URL: {}", url);
                return Err(Error::git2(
                    git2::Error::from_str("HTTP push requires credentials"),
                    "push",
                ));
            } else {
                debug!("HTTP push with credentials for URL: {}", url);
            }
        } else {
            debug!("Non-HTTP push for URL: {}", url);
        }
    } else {
        error!("Could not get remote URL for push");
    }

    let branch = current_branch(repo)?;

    // Try normal push first
    let refspecs = [format!("refs/heads/{branch}:refs/heads/{branch}")];
    let mut callbacks = RemoteCallbacks::new();
    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));

    if let Some(c) = &cred {
        callbacks.credentials(move |_url, _username_from_url, _allowed_types| credential_helper(c));
    }

    let mut push_opts = PushOptions::new();
    push_opts.remote_callbacks(callbacks);

    match remote.push(&refspecs, Some(&mut push_opts)) {
        Ok(()) => return Ok(()),
        Err(e) if e.raw_code() == -11 => {
            // GIT_ENONFASTFORWARD
            // Instead of force push, return the non-fast-forward error
            // The app can handle this by doing a sync operation
            warn!("Push failed with non-fast-forward - app should sync first");
            Err(Error::git2(e, "push needs sync"))
        }
        Err(e) => {
            if let Some(url) = remote.url() {
                if url.contains("github.com") {
                    error!(
                        "GitHub push failed - ensure you're using a Personal Access Token, not your password"
                    );
                    Err(Error::git2(
                        e,
                        "GitHub push failed - check credentials (use Personal Access Token)",
                    ))
                } else if url.contains("bitbucket.org") {
                    error!(
                        "Bitbucket push failed - ensure you're using an App Password, not your password"
                    );
                    Err(Error::git2(
                        e,
                        "Bitbucket push failed - check credentials (use App Password)",
                    ))
                } else if url.contains("gitlab.com") || url.contains("gitlab.") {
                    error!(
                        "GitLab push failed - ensure you're using a Personal Access Token, not your password"
                    );
                    Err(Error::git2(
                        e,
                        "GitLab push failed - check credentials (use Personal Access Token)",
                    ))
                } else if url.contains("azure.com") || url.contains("visualstudio.com") {
                    error!(
                        "Azure DevOps push failed - ensure you're using a Personal Access Token"
                    );
                    Err(Error::git2(
                        e,
                        "Azure DevOps push failed - check credentials (use Personal Access Token)",
                    ))
                } else if url.contains("codecommit.") {
                    error!("AWS CodeCommit push failed - check IAM permissions and credentials");
                    Err(Error::git2(
                        e,
                        "AWS CodeCommit push failed - check IAM permissions",
                    ))
                } else {
                    Err(Error::git2(e, "push"))
                }
            } else {
                Err(Error::git2(e, "push"))
            }
        }
    }
}

pub fn sync(cred: Option<Cred>) -> Result<(), Error> {
    apply_ssh_workaround(false);
    let mut repo_guard = REPO.lock().expect("repo lock");
    let repo = repo_guard.as_mut().expect("repo");

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, cannot sync");
        return Err(Error::git2(
            git2::Error::from_str("Repository in invalid state"),
            "sync",
        ));
    }

    let branch = current_branch(repo)?;

    // Stash any local changes
    let has_changes = repo
        .statuses(None)
        .map(|statuses| !statuses.is_empty())
        .unwrap_or(false);

    let stashed = if has_changes {
        match repo.stash_save(&repo.signature()?, "Auto-stash before sync", None) {
            Ok(_) => {
                info!("Stashed local changes");
                true
            }
            Err(e) => {
                warn!("Failed to stash changes: {}", e);
                false
            }
        }
    } else {
        false
    };

    // Fetch latest from remote
    let mut remote = repo
        .find_remote(REMOTE)
        .map_err(|e| Error::git2(e, "find_remote"))?;

    let mut callbacks = RemoteCallbacks::new();
    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));

    if let Some(c) = &cred {
        callbacks.credentials(move |_url, _username_from_url, _allowed_types| credential_helper(c));
    }

    let mut fetch_options = FetchOptions::new();
    fetch_options.remote_callbacks(callbacks);

    remote
        .fetch(&[] as &[&str], Some(&mut fetch_options), None)
        .map_err(|e| Error::git2(e, "fetch"))?;

    // Drop remote to release the borrow
    drop(remote);

    // Reset local branch to remote branch (this is like a hard pull)
    {
        let remote_branch_ref = format!("refs/remotes/{}/{}", REMOTE, branch);
        let remote_ref = repo
            .find_reference(&remote_branch_ref)
            .map_err(|e| Error::git2(e, "find remote reference"))?;

        let remote_commit = repo
            .find_commit(remote_ref.target().unwrap())
            .map_err(|e| Error::git2(e, "find remote commit"))?;

        // Reset local branch to remote
        repo.reset(remote_commit.as_object(), git2::ResetType::Hard, None)
            .map_err(|e| Error::git2(e, "reset to remote"))?;

        // Set head to the branch
        let local_ref_name = format!("refs/heads/{}", branch);
        repo.set_head(&local_ref_name)
            .map_err(|e| Error::git2(e, "set head"))?;
    }

    // Apply stashed changes if any (now all references are dropped)
    if stashed {
        if let Err(e) = repo.stash_apply(0, None) {
            warn!("Failed to apply stashed changes: {}", e);
            // Continue anyway - the sync succeeded
        } else {
            info!("Successfully applied stashed changes");
        }
    }

    info!("Sync completed successfully");
    Ok(())
}

pub fn pull(cred: Option<Cred>, name: &str, email: &str) -> Result<(), Error> {
    apply_ssh_workaround(false);

    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, cannot pull");
        return Err(Error::git2(
            git2::Error::from_str("Repository in invalid state"),
            "pull",
        ));
    }

    let mut remote = repo
        .find_remote(REMOTE)
        .map_err(|e| Error::git2(e, "find_remote"))?;

    let mut callbacks = RemoteCallbacks::new();

    callbacks.certificate_check(|_cert, _| Ok(CertificateCheckStatus::CertificateOk));

    if let Some(cred) = cred {
        callbacks
            .credentials(move |_url, _username_from_url, _allowed_types| credential_helper(&cred));
    }

    let mut fetch_options = FetchOptions::new();
    fetch_options.remote_callbacks(callbacks);

    let branch = current_branch(repo)?;

    // Remove FETCH_HEAD file to avoid corruption issues
    if let Ok(git_dir) = repo.path().canonicalize() {
        let fetch_head_path = git_dir.join("FETCH_HEAD");
        let _ = std::fs::remove_file(&fetch_head_path);
    }

    remote
        .fetch(&[] as &[&str], Some(&mut fetch_options), None)
        .map_err(|e| Error::git2(e, "fetch"))?;

    let fetch_head = match repo.find_reference("FETCH_HEAD") {
        Ok(r) => r,
        Err(_) => {
            // Try to delete corrupted FETCH_HEAD and refetch
            if let Ok(git_dir) = repo.path().canonicalize() {
                let fetch_head_path = git_dir.join("FETCH_HEAD");
                let _ = std::fs::remove_file(&fetch_head_path);
            }
            warn!("FETCH_HEAD was corrupted, attempting to refetch...");

            // Refetch
            remote
                .fetch(&[] as &[&str], Some(&mut fetch_options), None)
                .map_err(|e| Error::git2(e, "refetch after corruption"))?;

            repo.find_reference("FETCH_HEAD")
                .map_err(|e| Error::git2(e, "find_reference after refetch"))?
        }
    };

    let commit = repo
        .reference_to_annotated_commit(&fetch_head)
        .map_err(|e| Error::git2(e, "reference_to_annotated_commit"))?;

    merge::do_merge(repo, &branch, commit, name, email).map_err(|e| Error::git2(e, "do_merge"))?;

    Ok(())
}

pub fn close() {
    let mut repo = REPO.lock().expect("repo lock");
    repo.take();
}

pub fn cleanup_repo() -> Result<(), Error> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");
    cleanup_repo_state(repo)
}

pub fn is_change() -> Result<bool, Error> {
    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, assuming no changes");
        return Ok(false);
    }

    let mut opts = StatusOptions::new();
    opts.include_untracked(true).recurse_untracked_dirs(true);

    let statuses = repo
        .statuses(Some(&mut opts))
        .map_err(|e| Error::git2(e, "statuses"))?;

    let count = statuses.len();

    Ok(count > 0)
}

pub fn get_timestamps() -> Result<HashMap<String, i64>, Error> {
    let start = std::time::Instant::now();
    log::debug!("Starting get_timestamps");

    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // Verify the repository is still valid by checking if we can access basic properties
    if let Err(_) = repo.head() {
        warn!("Repository appears to be in invalid state, returning empty timestamps");
        return Ok(HashMap::new());
    }

    // First, get the list of all supported files in the repo at HEAD
    let head = repo.head()?.peel_to_commit()?;
    let tree = head.tree()?;
    let mut supported_files = std::collections::HashSet::new();

    tree.walk(TreeWalkMode::PreOrder, |root, entry| {
        if entry.kind() == Some(git2::ObjectType::Blob)
            && let Some(name) = entry.name()
            && let Some(extension) = Path::new(name).extension()
            && let Some(extension) = extension.to_str()
            && is_extension_supported(extension)
        {
            let path = format!("{root}{name}");
            supported_files.insert(path);
        }
        TreeWalkResult::Ok
    })?;

    log::debug!("Found {} supported files to process", supported_files.len());

    if supported_files.is_empty() {
        return Ok(HashMap::new());
    }

    let mut file_timestamps = HashMap::new();

    // Walk through commits in reverse chronological order (newest first)
    let mut revwalk = repo.revwalk()?;
    revwalk.push_head()?;
    revwalk.set_sorting(git2::Sort::TIME)?;

    let mut commits_processed = 0;

    for oid_result in revwalk {
        commits_processed += 1;

        let oid = oid_result?;
        let commit = repo.find_commit(oid)?;
        let commit_time = commit.time().seconds() * 1000;

        // Check if this commit modifies any files
        let parent = commit.parents().next();

        if let Some(parent) = parent {
            if let (Ok(parent_tree), Ok(current_tree)) = (parent.tree(), commit.tree()) {
                // Get diff between this commit and its parent
                if let Ok(diff) =
                    repo.diff_tree_to_tree(Some(&parent_tree), Some(&current_tree), None)
                {
                    // Check each delta to see what files were modified
                    for delta in diff.deltas() {
                        if let Some(path) = delta.new_file().path() {
                            if let Some(path_str) = path.to_str() {
                                // If this file is in our supported files list and we haven't seen it before
                                if supported_files.contains(path_str)
                                    && !file_timestamps.contains_key(path_str)
                                {
                                    file_timestamps.insert(path_str.to_string(), commit_time);
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Initial commit - all files are "modified"
            if let Ok(tree) = commit.tree() {
                tree.walk(TreeWalkMode::PreOrder, |root, entry| {
                    if entry.kind() == Some(git2::ObjectType::Blob)
                        && let Some(name) = entry.name()
                    {
                        let path = format!("{root}{name}");
                        if supported_files.contains(&path) && !file_timestamps.contains_key(&path) {
                            file_timestamps.insert(path, commit_time);
                        }
                    }
                    TreeWalkResult::Ok
                })?;
            }
        }

        // If we've found timestamps for all supported files, we can stop
        if file_timestamps.len() >= supported_files.len() {
            log::debug!(
                "Found timestamps for all {} files after {} commits",
                supported_files.len(),
                commits_processed
            );
            break;
        }
    }

    // Check if we have timestamps for all files
    let missing_files: Vec<String> = supported_files
        .iter()
        .filter(|file| !file_timestamps.contains_key(*file))
        .cloned()
        .collect();

    if !missing_files.is_empty() {
        // Use current time for missing files and log an error
        let current_time = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis() as i64;

        for file in &missing_files {
            file_timestamps.insert(file.clone(), current_time);
        }

        error!(
            "Some files were never committed, using current time for {} files: {:?}",
            missing_files.len(),
            missing_files
        );
    }

    let duration = start.elapsed();
    log::debug!(
        "get_timestamps completed in {}ms, processed {} commits, found timestamps for {} files",
        duration.as_millis(),
        commits_processed,
        file_timestamps.len()
    );

    Ok(file_timestamps)
}

#[derive(Debug)]
pub struct GitLogEntry {
    pub hash: String,
    pub message: String,
    pub author: String,
    pub date: String,
}

pub fn get_git_log(limit: usize) -> Result<Vec<GitLogEntry>, Error> {
    let start = std::time::Instant::now();
    log::debug!("Starting get_git_log with limit {}", limit);

    let repo = REPO.lock().expect("repo lock");
    let repo = repo.as_ref().expect("repo");

    // Verify the repository is still valid
    if repo.head().is_err() {
        warn!("Repository appears to be in invalid state, returning empty log");
        return Ok(Vec::new());
    }

    let mut revwalk = repo.revwalk()?;
    revwalk.push_head()?;

    let mut entries = Vec::new();

    for oid in revwalk.take(limit) {
        let oid = oid?;
        let commit = repo.find_commit(oid)?;

        let hash = oid.to_string();
        let message = commit.message().unwrap_or("").to_string();
        let author = commit.author().name().unwrap_or("").to_string();

        // Format date as readable string in local timezone
        let time = commit.time();
        let datetime = DateTime::from_timestamp(time.seconds(), 0).unwrap_or(DateTime::UNIX_EPOCH);
        let local_datetime = datetime.with_timezone(&Local);
        let date = local_datetime.format("%Y-%m-%d %H:%M:%S").to_string();

        entries.push(GitLogEntry {
            hash,
            message,
            author,
            date,
        });
    }

    let duration = start.elapsed();
    log::debug!(
        "get_git_log completed in {}ms, returned {} entries",
        duration.as_millis(),
        entries.len()
    );

    Ok(entries)
}
