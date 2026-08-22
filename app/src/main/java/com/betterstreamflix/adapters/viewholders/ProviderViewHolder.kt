package com.betterstreamflix.adapters.viewholders

import android.content.Intent
import android.graphics.drawable.PictureDrawable
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.betterstreamflix.R
import com.betterstreamflix.databinding.ItemProviderMobileBinding
import com.betterstreamflix.databinding.ItemProviderTvBinding
import com.betterstreamflix.models.Provider
import com.betterstreamflix.utils.UserPreferences
import com.betterstreamflix.utils.toActivity
import java.util.Locale

class ProviderViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    private lateinit var provider: Provider

    fun bind(provider: Provider) {
        this.provider = provider

        when (_binding) {
            is ItemProviderMobileBinding -> displayMobileItem(_binding)
            is ItemProviderTvBinding -> displayTvItem(_binding)
        }
    }

    private fun displayMobileItem(binding: ItemProviderMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                selectProvider()
            }
        }

        binding.ivProviderFavorite.apply {
            updateFavoriteIcon(binding.ivProviderFavorite, provider.isFavorite)
            setOnClickListener {
                toggleFavorite()
                updateFavoriteIcon(binding.ivProviderFavorite, provider.isFavorite)
            }
        }

        loadProviderLogo(binding.ivProviderLogo)

        binding.tvProviderName.text = provider.name

        binding.tvProviderLanguage.text = Locale.forLanguageTag(provider.language)
            .let { it.getDisplayLanguage(it) }
            .replaceFirstChar { it.titlecase() }
    }

    private fun displayTvItem(binding: ItemProviderTvBinding) {
        binding.root.apply {
            setOnClickListener {
                selectProvider()
            }
        }

        binding.ivProviderFavorite.apply {
            updateFavoriteIcon(binding.ivProviderFavorite, provider.isFavorite)
            setOnClickListener {
                toggleFavorite()
                updateFavoriteIcon(binding.ivProviderFavorite, provider.isFavorite)
            }
        }

        loadProviderLogo(binding.ivProviderLogo)

        binding.tvProviderName.text = provider.name

        binding.tvProviderLanguage.text = Locale.forLanguageTag(provider.language)
            .let { it.getDisplayLanguage(it) }
            .replaceFirstChar { it.titlecase() }
    }

    private fun selectProvider() {
        UserPreferences.currentProvider = provider.provider
        context.toActivity()?.apply {
            startActivity(
                Intent(this, this::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            )
            finish()
        }
    }

    private fun toggleFavorite() {
        provider.isFavorite = !provider.isFavorite
        val favorites = UserPreferences.favoriteProviders.toMutableSet()
        if (provider.isFavorite) {
            favorites.add(provider.name)
        } else {
            favorites.remove(provider.name)
        }
        UserPreferences.favoriteProviders = favorites
    }

    private fun updateFavoriteIcon(imageView: ImageView, isFavorite: Boolean) {
        if (isFavorite) {
            imageView.setImageResource(R.drawable.ic_favorite_enable)
            imageView.setColorFilter(context.getColor(R.color.favorite_selected))
        } else {
            imageView.setImageResource(R.drawable.ic_favorite_disable)
            imageView.setColorFilter(0xFF666666.toInt())
        }
    }

    private fun loadProviderLogo(imageView: ImageView) {
        val logo = provider.logo.takeIf { it.isNotEmpty() }
        val isSvg = logo?.substringBefore("?")?.endsWith(".svg", ignoreCase = true) == true

        if (isSvg) {
            imageView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            Glide.with(context)
                .`as`(PictureDrawable::class.java)
                .load(logo)
                .error(R.drawable.ic_provider_default_logo)
                .into(imageView)
        } else {
            imageView.setLayerType(View.LAYER_TYPE_NONE, null)
            Glide.with(context)
                .load(logo ?: R.drawable.ic_provider_default_logo)
                .error(R.drawable.ic_provider_default_logo)
                .fitCenter()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(imageView)
        }
    }
}
