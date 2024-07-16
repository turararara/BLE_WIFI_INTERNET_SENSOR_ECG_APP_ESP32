package com.example.pfe


import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.pfe.animation.startAnimation
import com.example.pfe.databinding.ActivityMainBinding
import com.example.pfe.reseaux.bluetooth.BleDashboard
import com.example.pfe.reseaux.internet.InternetDashboard
import com.example.pfe.reseaux.wifi.WifiDashboard

class MainActivity : AppCompatActivity() {
    private lateinit var binding : ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        buttons()
    }
    private fun buttons(){
        binding.INTERNETButton.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this,R.anim.circle_explosion_anim).apply{
                duration = 400
                interpolator= AccelerateDecelerateInterpolator()
            }
            binding.INTERNETEXPLOSION.isVisible=true
            binding.INTERNETEXPLOSION.startAnimation(animation){
                binding.cover.setBackgroundColor(ContextCompat.getColor(this,R.color.green))
                val intent = Intent(this, InternetDashboard::class.java)
                startActivity(intent)
                if(Build.VERSION.SDK_INT >= 34){
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,R.anim.slide_in_up,R.anim.slide_out_up)
                }else{
                    overridePendingTransition(R.anim.slide_in_up,R.anim.slide_out_up)
                }
                finish()
            }
        }
        binding.BLEButton.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this,R.anim.circle_explosion_anim).apply{
                duration = 400
                interpolator= AccelerateDecelerateInterpolator()
            }
            binding.BLEEXPLOSION.isVisible=true
            binding.BLEEXPLOSION.startAnimation(animation){
                binding.cover.setBackgroundColor(ContextCompat.getColor(this,R.color.blue))
                val intent = Intent(this, BleDashboard::class.java)
                startActivity(intent)
                if(Build.VERSION.SDK_INT >= 34){
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,R.anim.slide_in_up,R.anim.slide_out_up)
                }else{
                    overridePendingTransition(R.anim.slide_in_up,R.anim.slide_out_up)
                }
                finish()
            }
        }
        binding.WIFIButton.setOnClickListener {
            val animation = AnimationUtils.loadAnimation(this,R.anim.circle_explosion_anim).apply{
                duration = 400
                interpolator= AccelerateDecelerateInterpolator()
            }
            binding.WIFIEXPLOSION.isVisible=true
            binding.WIFIEXPLOSION.startAnimation(animation){
                binding.cover.setBackgroundColor(ContextCompat.getColor(this,R.color.purple))
                val intent = Intent(this, WifiDashboard::class.java)
                startActivity(intent)
                if(Build.VERSION.SDK_INT >= 34){
                    overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE,R.anim.slide_in_up,R.anim.slide_out_up)
                }else{
                    overridePendingTransition(R.anim.slide_in_up,R.anim.slide_out_up)
                }
                finish()
            }
        }
    }


}

