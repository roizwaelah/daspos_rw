package com.daspos.feature.report;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.daspos.R;
import com.daspos.model.ReportItem;
import com.daspos.shared.util.CurrencyUtils;

import java.util.ArrayList;
import java.util.List;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.VH> {
    public interface Listener {
        void onReportItemClicked(ReportItem item);
    }

    private final List<ReportItem> items = new ArrayList<>();
    private final Listener listener;

    public ReportAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<ReportItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public java.util.List<ReportItem> getItems() {
        return new java.util.ArrayList<>(items);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_report_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        ReportItem item = items.get(position);
        holder.tvId.setText(item.getId());
        holder.tvTime.setText(item.getTime());
        holder.tvTotal.setText(CurrencyUtils.formatRupiah(item.getTotal()));
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (listener != null) listener.onReportItemClicked(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvId, tvTime, tvTotal;
        VH(@NonNull View itemView) {
            super(itemView);
            tvId = (TextView) ((ViewGroup)itemView).getChildAt(0);
            tvTime = (TextView) ((ViewGroup)itemView).getChildAt(1);
            tvTotal = (TextView) ((ViewGroup)itemView).getChildAt(2);
        }
    }
}
