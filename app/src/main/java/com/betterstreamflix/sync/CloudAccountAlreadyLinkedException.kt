package com.betterstreamflix.sync

/**
 * The same Supabase Auth account is already linked to another local profile
 * on this installation.
 */
class CloudAccountAlreadyLinkedException(
    val existingProfileId: String,
    val existingProfileName: String,
) : IllegalStateException(
    "This Supabase account is already linked to the local profile \"$existingProfileName\". " +
        "Sign out there first, or use a different account.",
)
