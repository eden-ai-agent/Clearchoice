package com.example.clearchoice

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            var selectedFragment: Fragment? = null
            when (menuItem.itemId) {
                R.id.navigation_record -> {
                    selectedFragment = RecordFragment()
                }
                R.id.navigation_sessions -> {
                    selectedFragment = SessionListFragment()
                }
            }
            if (selectedFragment != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, selectedFragment)
                    .commit()
            }
            true
        }

        // Set default fragment
        if (savedInstanceState == null) {
            bottomNavigationView.selectedItemId = R.id.navigation_record
        }
    }

    // Optional: Add onRequestPermissionsResult if handling permission callbacks here
    // override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    //     super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    //     // Forward to fragment, or handle here if RecordFragment is always active.
    //     // This is basic, a more robust solution might involve a shared ViewModel or event bus.
    //     val fragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
    //     fragment?.onRequestPermissionsResult(requestCode, permissions, grantResults)
    // }
}
