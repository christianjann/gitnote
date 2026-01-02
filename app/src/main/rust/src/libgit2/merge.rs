use git2::Repository;

fn fast_forward(
    repo: &Repository,
    lb: &mut git2::Reference,
    rc: &git2::AnnotatedCommit,
) -> Result<(), git2::Error> {
    let name = match lb.name() {
        Some(s) => s.to_string(),
        None => String::from_utf8_lossy(lb.name_bytes()).to_string(),
    };
    let msg = format!("Fast-Forward: Setting {} to id: {}", name, rc.id());
    lb.set_target(rc.id(), &msg)?;
    repo.set_head(&name)?;
    repo.checkout_head(Some(
        git2::build::CheckoutBuilder::default()
            // For some reason the force is required to make the working directory actually get updated
            // I suspect we should be adding some logic to handle dirty working directory states
            // but this is just an example so maybe not.
            .force(),
    ))?;
    Ok(())
}

fn normal_merge(
    repo: &Repository,
    local: &git2::AnnotatedCommit,
    remote: &git2::AnnotatedCommit,
    name: &str,
    email: &str,
) -> Result<(), git2::Error> {
    // Set up the merge state
    let mut merge_options = git2::MergeOptions::new();
    let mut checkout_options = git2::build::CheckoutBuilder::new();
    checkout_options
        .allow_conflicts(true)
        .conflict_style_merge(true)
        .force();
    repo.merge(&[remote], Some(&mut merge_options), Some(&mut checkout_options))?;
    
    // Get the repository index which now has conflicts
    let mut idx = repo.index()?;

    let result_tree = if idx.has_conflicts() {
        info!("Merge conflicts detected, attempting automatic resolution...");
        
        // First pass: resolve conflicts where we have our version
        let mut resolved_count = 0;
        let conflicts: Vec<_> = idx.conflicts()?.collect::<Result<Vec<_>, _>>()?;
        
        for conflict in &conflicts {
            if let Some(our_entry) = &conflict.our {
                // Resolve by writing the our version to the working directory
                let blob = repo.find_blob(our_entry.id)?;
                let content = blob.content();
                let path_str = std::str::from_utf8(&our_entry.path).unwrap_or("");
                
                // Get the full path relative to repository working directory
                let repo_workdir = repo.workdir().ok_or_else(|| git2::Error::from_str("Repository has no working directory"))?;
                let full_path = repo_workdir.join(path_str);
                
                // Ensure parent directories exist
                if let Some(parent) = full_path.parent() {
                    std::fs::create_dir_all(parent).map_err(|e| git2::Error::from_str(&format!("Failed to create directories: {}", e)))?;
                }
                
                std::fs::write(&full_path, content).map_err(|e| git2::Error::from_str(&format!("Failed to write file: {}", e)))?;
                
                // Add the resolved file to the index
                idx.add_path(&std::path::Path::new(path_str))?;
                
                // Mark conflict as resolved
                let path = std::path::Path::new(path_str);
                if let Err(e) = idx.conflict_remove(path) {
                    warn!("Failed to remove conflict for {:?}: {}", path_str, e);
                } else {
                    resolved_count += 1;
                    info!("Resolved conflict for file: {:?}", String::from_utf8_lossy(&our_entry.path));
                }
            }
        }
        
        // Second pass: for remaining conflicts, try their version if ours doesn't exist
        if idx.has_conflicts() {
            for conflict in &conflicts {
                if conflict.our.is_none() && conflict.their.is_some() {
                    let their_entry = conflict.their.as_ref().unwrap();
                    // Resolve by writing the their version to the working directory
                    let blob = repo.find_blob(their_entry.id)?;
                    let content = blob.content();
                    let path_str = std::str::from_utf8(&their_entry.path).unwrap_or("");
                    
                    // Get the full path relative to repository working directory
                    let repo_workdir = repo.workdir().ok_or_else(|| git2::Error::from_str("Repository has no working directory"))?;
                    let full_path = repo_workdir.join(path_str);
                    
                    // Ensure parent directories exist
                    if let Some(parent) = full_path.parent() {
                        std::fs::create_dir_all(parent).map_err(|e| git2::Error::from_str(&format!("Failed to create directories: {}", e)))?;
                    }
                    
                    std::fs::write(&full_path, content).map_err(|e| git2::Error::from_str(&format!("Failed to write file: {}", e)))?;
                    
                    // Add the resolved file to the index
                    idx.add_path(&std::path::Path::new(path_str))?;
                    
                    // Mark conflict as resolved
                    let path = std::path::Path::new(path_str);
                    if let Err(e) = idx.conflict_remove(path) {
                        warn!("Failed to remove conflict for {:?}: {}", path_str, e);
                    } else {
                        resolved_count += 1;
                        info!("Resolved conflict (no local version) for file: {:?}", 
                             String::from_utf8_lossy(&their_entry.path));
                    }
                }
            }
        }
        
        // Third pass: for any remaining conflicts, try ancestor version
        if idx.has_conflicts() {
            for conflict in &conflicts {
                if conflict.our.is_none() && conflict.their.is_none() && conflict.ancestor.is_some() {
                    let ancestor_entry = conflict.ancestor.as_ref().unwrap();
                    // Resolve by writing the ancestor version to the working directory
                    let blob = repo.find_blob(ancestor_entry.id)?;
                    let content = blob.content();
                    let path_str = std::str::from_utf8(&ancestor_entry.path).unwrap_or("");
                    
                    // Get the full path relative to repository working directory
                    let repo_workdir = repo.workdir().ok_or_else(|| git2::Error::from_str("Repository has no working directory"))?;
                    let full_path = repo_workdir.join(path_str);
                    
                    // Ensure parent directories exist
                    if let Some(parent) = full_path.parent() {
                        std::fs::create_dir_all(parent).map_err(|e| git2::Error::from_str(&format!("Failed to create directories: {}", e)))?;
                    }
                    
                    std::fs::write(&full_path, content).map_err(|e| git2::Error::from_str(&format!("Failed to write file: {}", e)))?;
                    
                    // Add the resolved file to the index
                    idx.add_path(&std::path::Path::new(path_str))?;
                    
                    // Mark conflict as resolved
                    let path = std::path::Path::new(path_str);
                    if let Err(e) = idx.conflict_remove(path) {
                        warn!("Failed to remove conflict for {:?}: {}", path_str, e);
                    } else {
                        resolved_count += 1;
                        info!("Resolved conflict (using ancestor) for file: {:?}", 
                             String::from_utf8_lossy(&ancestor_entry.path));
                    }
                }
            }
        }
        
        info!("Resolved {}/{} conflicts", resolved_count, conflicts.len());
        
        // Write the tree to repository
        let tree_id = idx.write_tree_to(repo)?;
        
        // Checkout the resolved tree to update working directory
        let result_tree = repo.find_tree(tree_id)?;
        repo.checkout_tree(
            result_tree.as_object(),
            Some(git2::build::CheckoutBuilder::default()
                .force()
                .allow_conflicts(false)
            )
        )?;
        
        // Add all resolved files to the repository index
        let mut repo_index = repo.index()?;
        repo_index.add_all(["*"].iter(), git2::IndexAddOption::DEFAULT, None)?;
        repo_index.write()?;
        
        // Verify no conflicts remain
        if repo_index.has_conflicts() {
            error!("Could not resolve all conflicts automatically (resolved {}/{}). Aborting merge.", 
                  resolved_count, conflicts.len());
            
            // Clean up: reset to local head
            let local_commit = repo.find_commit(local.id())?;
            repo.reset(local_commit.as_object(), git2::ResetType::Hard, None)?;
            return Err(git2::Error::from_str("Could not resolve all merge conflicts automatically"));
        }
        
        // Write the resolved index and create tree
        idx.write()?;
        let tree_id = idx.write_tree()?;
        let result_tree = repo.find_tree(tree_id)?;
        
        result_tree
    } else {
        // No conflicts, just write the tree
        idx.write()?;
        let tree_id = idx.write_tree()?;
        repo.find_tree(tree_id)?
    };
    // now create the merge commit
    let msg = format!("Merge: {} into {}", remote.id(), local.id());
    let sig = git2::Signature::now(name, email)?;
    let local_commit = repo.find_commit(local.id())?;
    let remote_commit = repo.find_commit(remote.id())?;
    // Do our merge commit and set current branch head to that commit.
    let _merge_commit = repo.commit(
        Some("HEAD"),
        &sig,
        &sig,
        &msg,
        &result_tree,
        &[&local_commit, &remote_commit],
    )?;
    // Set working tree to match head.
    let mut checkout_opts = git2::build::CheckoutBuilder::new();
    checkout_opts.force();
    repo.checkout_head(Some(&mut checkout_opts))?;
    Ok(())
}

pub fn do_merge<'a>(
    repo: &'a Repository,
    remote_branch: &str,
    fetch_commit: git2::AnnotatedCommit<'a>,
    name: &str,
    email: &str,
) -> Result<(), git2::Error> {
    // 1. do a merge analysis
    let analysis = repo.merge_analysis(&[&fetch_commit])?;

    // 2. Do the appropriate merge
    if analysis.0.is_fast_forward() {
        // do a fast forward
        let refname = format!("refs/heads/{remote_branch}");
        match repo.find_reference(&refname) {
            Ok(mut r) => {
                fast_forward(repo, &mut r, &fetch_commit)?;
            }
            Err(_) => {
                // The branch doesn't exist so just set the reference to the
                // commit directly. Usually this is because you are pulling
                // into an empty repository.
                repo.reference(
                    &refname,
                    fetch_commit.id(),
                    true,
                    &format!("Setting {} to {}", remote_branch, fetch_commit.id()),
                )?;
                repo.set_head(&refname)?;
                repo.checkout_head(Some(
                    git2::build::CheckoutBuilder::default()
                        .allow_conflicts(true)
                        .conflict_style_merge(true)
                        .force(),
                ))?;
            }
        };
    } else if analysis.0.is_normal() {
        // do a normal merge
        let head_commit = repo.reference_to_annotated_commit(&repo.head()?)?;
        normal_merge(repo, &head_commit, &fetch_commit, name, email)?;
    } else {
        // Nothing to do...
    }
    Ok(())
}
