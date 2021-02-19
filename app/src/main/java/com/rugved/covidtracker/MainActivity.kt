package com.rugved.covidtracker

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.google.gson.GsonBuilder
import com.robinhood.ticker.TickerUtils
import com.rugved.covidtracker.databinding.ActivityMainBinding
import org.w3c.dom.Text
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private const val BASE_URL = "https://covidtracking.com/api/v1/"
private const val TAG = "MainActivity"
private const val ALL_STATES: String = "All (Nationwide)"

private lateinit var binding: ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var currentlyShownData: List<CovidData>
    private lateinit var adapt: CovidSparkAdapter
    private lateinit var nationalDailyData: List<CovidData>
    private lateinit var perStateDailyData: Map<String, List<CovidData>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        supportActionBar?.title = getString(R.string.app_description)

        val gson = GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss").create()
        val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
        val covidService = retrofit.create(CovidService::class.java)

        //Fetch US National Data
        covidService.getNationsData().enqueue(object : Callback<List<CovidData>> {
            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "onResponse: $response")
                val nationalData = response.body()
                if (nationalData == null) {
                    Log.w(TAG, "onResponse: Did not receive a valid response body")
                    return
                }
                setupEventListeners()
                nationalDailyData = nationalData.reversed()
                Log.i(TAG, "onResponse: Update graph with national data")
                updateDisplaywithData(nationalDailyData)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure: $t")
            }

        })

        //Fetch state data
        covidService.getStatesData().enqueue(object: Callback<List<CovidData>>{
            override fun onResponse(call: Call<List<CovidData>>, response: Response<List<CovidData>>) {
                Log.i(TAG, "onResponse: $response")
                val statesData = response.body()
                if (statesData == null) {
                    Log.w(TAG, "onResponse: Did not receive a valid response body")
                    return
                }
                perStateDailyData = statesData.reversed().groupBy { it.state }
                Log.i(TAG, "onResponse: Update spinner with state names")
                // update spinner with state names
                updateSpinnerWithStateData(perStateDailyData.keys)
            }

            override fun onFailure(call: Call<List<CovidData>>, t: Throwable) {
                Log.e(TAG, "onFailure: $t")
            }
        })
    }

    private fun updateSpinnerWithStateData(stateNames: Set<String>) {
        val stateAbbreviationsList = stateNames.toMutableList()
        stateAbbreviationsList.sort()
        stateAbbreviationsList.add(0, ALL_STATES)

        // add states list as input for spinner
        //val niceSpinner = binding.niceSpinner
        binding.niceSpinner.attachDataSource(stateAbbreviationsList)
        binding.niceSpinner.setOnSpinnerItemSelectedListener { parent, _, position, _ ->
            val selectedState = parent.getItemAtPosition(position) as String
            val selectedData = perStateDailyData[selectedState] ?: nationalDailyData
            updateDisplaywithData(selectedData)
        }
    }

    private fun setupEventListeners() {
        binding.textViewMetricLabel.setCharacterLists(TickerUtils.provideNumberList())
        //Add listener for user scrubbing on chart
        binding.sparkView.isScrubEnabled = true
        binding.sparkView.setScrubListener { itemData ->
            if(itemData is CovidData) {
                updateInforForDate(itemData)
            }
        }

        //Respond to radio buttons
        binding.radioTimeScaleSelection.setOnCheckedChangeListener{_, checkedId ->
            adapt.daysAgo = when (checkedId){
                R.id.radioButtonWeek -> TimeScale.WEEK
                R.id.radioButtonMonth -> TimeScale.MONTH
                else -> TimeScale.MAX
            }
            adapt.notifyDataSetChanged()
        }

        binding.radioGroupMetricSelection.setOnCheckedChangeListener { _, checkedId ->
            when(checkedId) {
                R.id.radioButtonNegative -> updateDisplayMetric(Metric.NEGATIVE)
                R.id.radioButtonPositive -> updateDisplayMetric(Metric.POSITIVE)
                R.id.radioButtonDeath -> updateDisplayMetric(Metric.DEATH)
            }
        }
    }

    private fun updateDisplayMetric(metric: Metric) {
        // update color of chart
        val colorRes = when (metric){
            Metric.DEATH -> R.color.death
            Metric.POSITIVE -> R.color.positive
            Metric.NEGATIVE -> R.color.negative
        }
        @ColorInt val colorInt = ContextCompat.getColor(this, colorRes)
        binding.sparkView.lineColor = colorInt
        binding.textViewMetricLabel.setTextColor(colorInt)
        // updating metric on adapter
        adapt.metric = metric
        adapt.notifyDataSetChanged()

        //reset number and date in bottom textviews
        updateInforForDate(currentlyShownData.last())
    }

    private fun updateDisplaywithData(dailyData: List<CovidData>) {
        currentlyShownData = dailyData
        //Create spark adapter
        adapt = CovidSparkAdapter(dailyData)
        binding.sparkView.adapter = adapt

        //update radio buttons to select positive and max tim by default
        binding.radioButtonPositive.isChecked = true;
        binding.radioButtonMax.isChecked = true;

        //display metric for the most recent data
        updateDisplayMetric(Metric.POSITIVE)
    }

    private fun updateInforForDate(covidData: CovidData) {
        val numCases = when (adapt.metric){
            Metric.NEGATIVE -> covidData.negativeIncrease
            Metric.POSITIVE -> covidData.positiveIncrease
            Metric.DEATH -> covidData.deathIncrease
        }
        binding.textViewMetricLabel.text = NumberFormat.getInstance().format(numCases)
        val outputDateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        binding.textViewDate.text = outputDateFormat.format(covidData.dateChecked)
    }
}