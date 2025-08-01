package com.example.ppg.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.ppg.R;
import com.example.ppg.models.Measurement;

import java.util.List;

public class MeasurementAdapter extends BaseAdapter {
    private Context context;
    private List<Measurement> measurements;
    private LayoutInflater inflater;

    public MeasurementAdapter(Context context, List<Measurement> measurements) {
        this.context = context;
        this.measurements = measurements;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return measurements.size();
    }

    @Override
    public Object getItem(int position) {
        return measurements.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_measurement, parent, false);
            holder = new ViewHolder();
            holder.timestampText = convertView.findViewById(R.id.timestampText);
            holder.heartRateText = convertView.findViewById(R.id.heartRateText);
            holder.bpText = convertView.findViewById(R.id.bpText);
            holder.qualityText = convertView.findViewById(R.id.qualityText);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Measurement measurement = measurements.get(position);
        
        // Set timestamp
        holder.timestampText.setText(measurement.getFormattedTimestamp());
        
        // Set heart rate
        holder.heartRateText.setText(String.format("%d BPM", measurement.getHeartRate()));
        
        // Set BP category with color
        if (measurement.getBpCategory() != null) {
            holder.bpText.setText(measurement.getBpDisplayText());
            
            // Set color based on BP category
            switch (measurement.getBpCategory().toLowerCase()) {
                case "normotensive":
                    holder.bpText.setTextColor(context.getResources().getColor(android.R.color.holo_green_dark));
                    break;
                case "prehypertensive":
                    holder.bpText.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
                    break;
                case "hypertensive":
                    holder.bpText.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
                    break;
                default:
                    holder.bpText.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
                    break;
            }
        } else {
            holder.bpText.setText("No BP Data");
            holder.bpText.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
        }
        
        // Set quality
        holder.qualityText.setText(String.format("Quality: %s (%d%%)", 
            measurement.getSignalQuality(), measurement.getHeartRateConfidence()));

        return convertView;
    }

    static class ViewHolder {
        TextView timestampText;
        TextView heartRateText;
        TextView bpText;
        TextView qualityText;
    }
}
