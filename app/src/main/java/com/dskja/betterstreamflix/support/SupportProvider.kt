package com.dskja.betterstreamflix.support

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.dskja.betterstreamflix.R

enum class SupportProvider(
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
    @StringRes val ctaRes: Int,
    @DrawableRes val iconRes: Int,
    val url: String,
    val telegramDeepLink: Boolean = false,
    val marksAppreciation: Boolean = false,
) {
    BUY_ME_A_COFFEE(
        titleRes = R.string.support_provider_bmc_title,
        descriptionRes = R.string.support_provider_bmc_description,
        ctaRes = R.string.support_provider_bmc_cta,
        iconRes = R.drawable.ic_buy_me_a_coffee,
        url = SupportUrls.BUY_ME_A_COFFEE_URL,
        marksAppreciation = true,
    ),
    GITHUB_SPONSORS(
        titleRes = R.string.support_provider_sponsors_title,
        descriptionRes = R.string.support_provider_sponsors_description,
        ctaRes = R.string.support_provider_sponsors_cta,
        iconRes = R.drawable.ic_github,
        url = SupportUrls.GITHUB_SPONSORS_URL,
        marksAppreciation = true,
    ),
    PATREON(
        titleRes = R.string.support_provider_patreon_title,
        descriptionRes = R.string.support_provider_patreon_description,
        ctaRes = R.string.support_provider_patreon_cta,
        iconRes = R.drawable.ic_patreon,
        url = SupportUrls.PATREON_URL,
        marksAppreciation = true,
    ),
    DISCORD(
        titleRes = R.string.support_provider_discord_title,
        descriptionRes = R.string.support_provider_discord_description,
        ctaRes = R.string.support_provider_discord_cta,
        iconRes = R.drawable.ic_discord,
        url = SupportUrls.DISCORD_URL,
    ),
    TELEGRAM(
        titleRes = R.string.support_provider_telegram_title,
        descriptionRes = R.string.support_provider_telegram_description,
        ctaRes = R.string.support_provider_telegram_cta,
        iconRes = R.drawable.ic_telegram,
        url = SupportUrls.TELEGRAM_URL,
        telegramDeepLink = true,
    ),
    GITHUB_REPOSITORY(
        titleRes = R.string.support_provider_github_title,
        descriptionRes = R.string.support_provider_github_description,
        ctaRes = R.string.support_provider_github_cta,
        iconRes = R.drawable.ic_github,
        url = SupportUrls.GITHUB_REPOSITORY_URL,
    ),
}
