package shop.sabjiwala.kisankendra

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import shop.sabjiwala.kisankendra.utils.LocaleHelper
import shop.sabjiwala.kisankendra.utils.TranslationHelper

class WeatherForecastAdapter(
    private var items: List<ForecastDay>
) : RecyclerView.Adapter<WeatherForecastAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tvForecastDay)
        val tvDate: TextView = view.findViewById(R.id.tvForecastDate)
        val tvCondition: TextView = view.findViewById(R.id.tvForecastCondition)
        val tvRain: TextView = view.findViewById(R.id.tvForecastRain)
        val tvTemp: TextView = view.findViewById(R.id.tvForecastTemp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_weather_forecast, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.tvDay.text = item.dayName
        holder.tvDate.text = item.dateStr
        holder.tvCondition.text = item.condition
        holder.tvRain.text = "🌧️ ${item.rainProb}% Rain Chance"
        holder.tvTemp.text = "${item.maxTemp}° / ${item.minTemp}°"

        val activeLang = LocaleHelper.getPersistedLanguage(context)
        if (activeLang != "en") {
            CoroutineScope(Dispatchers.Main).launch {
                val transDay = TranslationHelper.translateText(context, item.dayName, activeLang)
                holder.tvDay.text = transDay

                val transCond = TranslationHelper.translateText(context, item.condition, activeLang)
                holder.tvCondition.text = transCond
            }
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<ForecastDay>) {
        this.items = newItems
        notifyDataSetChanged()
    }
}
