package com.example.cctest

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.cctest.databinding.ActivityInsuranceMallBinding

class InsuranceMallActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsuranceMallBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityInsuranceMallBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.cardLatestNews.setOnClickListener {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://nextjs.org/docs/app/guides/upgrading/version-15")
                )
            )
        }
    }
}
