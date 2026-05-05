package com.example.cctest

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.example.cctest.databinding.ActivityPersonalInfoDetailBinding
import com.example.cctest.feature.personalinfo.data.PersonalInfoRecord
import com.example.cctest.routing.AppContainer
import com.example.cctest.routing.model.RecordRef

class PersonalInfoDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalInfoDetailBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPersonalInfoDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.personal_info_detail_title)
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        val record = AppContainer.repository().getRecordById(intent.getStringExtra(EXTRA_RECORD_ID))
        val name = record?.name ?: intent.getStringExtra(EXTRA_NAME).orEmpty()
        val age = record?.age ?: intent.getIntExtra(EXTRA_AGE, 0)
        val job = record?.occupation ?: intent.getStringExtra(EXTRA_JOB).orEmpty()
        val city = record?.city ?: intent.getStringExtra(EXTRA_CITY).orEmpty()
        val phone = record?.phone ?: intent.getStringExtra(EXTRA_PHONE).orEmpty()
        val emptyValue = getString(R.string.personal_info_empty_value)
        val ageText = if (age > 0) getString(R.string.personal_info_detail_age_value, age) else emptyValue

        binding.textviewDetailContent.text = getString(
            R.string.personal_info_detail_format,
            name.ifEmpty { emptyValue },
            ageText,
            job.ifEmpty { emptyValue },
            city.ifEmpty { emptyValue },
            phone.ifEmpty { emptyValue }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            finish()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    companion object {
        const val EXTRA_RECORD_ID = "extra_record_id"
        const val EXTRA_NAME = "extra_name"
        const val EXTRA_AGE = "extra_age"
        const val EXTRA_JOB = "extra_job"
        const val EXTRA_CITY = "extra_city"
        const val EXTRA_PHONE = "extra_phone"

        fun createIntent(context: android.content.Context, recordRef: RecordRef): android.content.Intent {
            return android.content.Intent(context, PersonalInfoDetailActivity::class.java).apply {
                putExtra(EXTRA_RECORD_ID, recordRef.recordId)
                putExtra(EXTRA_NAME, recordRef.displayName)
                putExtra(EXTRA_PHONE, recordRef.phone)
                putExtra(EXTRA_CITY, recordRef.city)
            }
        }

        fun createIntent(context: android.content.Context, record: PersonalInfoRecord): android.content.Intent {
            return android.content.Intent(context, PersonalInfoDetailActivity::class.java).apply {
                putExtra(EXTRA_RECORD_ID, record.recordId)
                putExtra(EXTRA_NAME, record.name)
                putExtra(EXTRA_AGE, record.age)
                putExtra(EXTRA_JOB, record.occupation)
                putExtra(EXTRA_CITY, record.city)
                putExtra(EXTRA_PHONE, record.phone)
            }
        }
    }
}
