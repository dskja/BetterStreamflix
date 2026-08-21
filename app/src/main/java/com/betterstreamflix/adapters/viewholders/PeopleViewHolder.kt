package com.betterstreamflix.adapters.viewholders

import android.view.animation.AnimationUtils
import androidx.navigation.findNavController
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.betterstreamflix.R
import com.betterstreamflix.databinding.ItemPeopleMobileBinding
import com.betterstreamflix.databinding.ItemPeopleTvBinding
import com.betterstreamflix.fragments.movie.MovieMobileFragment
import com.betterstreamflix.fragments.movie.MovieMobileFragmentDirections
import com.betterstreamflix.fragments.movie.MovieTvFragment
import com.betterstreamflix.fragments.movie.MovieTvFragmentDirections
import com.betterstreamflix.fragments.tv_show.TvShowMobileFragment
import com.betterstreamflix.fragments.tv_show.TvShowMobileFragmentDirections
import com.betterstreamflix.fragments.tv_show.TvShowTvFragment
import com.betterstreamflix.fragments.tv_show.TvShowTvFragmentDirections
import com.betterstreamflix.models.People
import com.betterstreamflix.utils.getCurrentFragment
import com.betterstreamflix.utils.toActivity

class PeopleViewHolder(
    private val _binding: ViewBinding
) : RecyclerView.ViewHolder(
    _binding.root
) {

    private val context = itemView.context
    private lateinit var people: People

    fun bind(people: People) {
        this.people = people

        when (_binding) {
            is ItemPeopleMobileBinding -> displayMobileItem(_binding)
            is ItemPeopleTvBinding -> displayTvItem(_binding)
        }
    }


    private fun displayMobileItem(binding: ItemPeopleMobileBinding) {
        binding.root.apply {
            setOnClickListener {
                when (context.toActivity()?.getCurrentFragment()) {
                    is MovieMobileFragment -> findNavController().navigate(
                        MovieMobileFragmentDirections.actionMovieToPeople(
                            id = people.id,
                            name = people.name,
                            image = people.image,
                        )
                    )
                    is TvShowMobileFragment -> findNavController().navigate(
                        TvShowMobileFragmentDirections.actionTvShowToPeople(
                            id = people.id,
                            name = people.name,
                            image = people.image,
                        )
                    )
                }
            }
        }

        binding.ivPeopleImage.apply {
            clipToOutline = true
            Glide.with(context)
                .load(people.image)
                .placeholder(R.drawable.ic_person_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
        }

        binding.tvPeopleName.text = people.name
    }

    private fun displayTvItem(binding: ItemPeopleTvBinding) {
        binding.root.apply {
            setOnClickListener {
                when (context.toActivity()?.getCurrentFragment()) {
                    is MovieTvFragment -> findNavController().navigate(
                        MovieTvFragmentDirections.actionMovieToPeople(
                            id = people.id,
                            name = people.name,
                            image = people.image,
                        )
                    )
                    is TvShowTvFragment -> findNavController().navigate(
                        TvShowTvFragmentDirections.actionTvShowToPeople(
                            id = people.id,
                            name = people.name,
                            image = people.image,
                        )
                    )
                }
            }
            setOnFocusChangeListener { _, hasFocus ->
                // Applichiamo l'animazione solo all'immagine, non a tutto il root (che include il testo)
                val animation = when {
                    hasFocus -> AnimationUtils.loadAnimation(context, R.anim.zoom_in)
                    else -> AnimationUtils.loadAnimation(context, R.anim.zoom_out)
                }
                binding.ivPeopleImage.startAnimation(animation)
                animation.fillAfter = true
            }
        }

        binding.ivPeopleImage.apply {
            clipToOutline = true
            Glide.with(context)
                .load(people.image)
                .placeholder(R.drawable.ic_person_placeholder)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(this)
        }

        binding.tvPeopleName.text = people.name
    }
}