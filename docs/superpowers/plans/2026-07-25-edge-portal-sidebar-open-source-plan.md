# Edge Portal Sidebar open-source release plan

1. Record the approved compatibility boundary and public-release criteria in the design specification.
2. Update the Android version and all user-facing English and Simplified Chinese brand strings while retaining internal identifiers.
3. Replace the adaptive, legacy, and monochrome VectorDrawable artwork and supporting icon colors.
4. Replace the README with public-facing feature, setup, privacy, permission, compatibility, and troubleshooting documentation.
5. Add Apache-2.0 licensing, contribution guidance, security reporting, and third-party trademark/unofficial-project notices.
6. Scan the current tree and Git history for credentials, machine-specific paths, device serials, and personal identifiers; fix any release-relevant findings.
7. Run unit tests, Android Lint, debug assembly, XML/resource validation through the Android build, and whitespace checks.
8. Review the final diff, commit it, and push it while the repository remains private.
9. Rename the GitHub repository and local remote, update repository metadata, then switch visibility to public.
10. Verify GitHub visibility, default branch, README rendering, license detection, repository description, remote URL, and HEAD commit.
