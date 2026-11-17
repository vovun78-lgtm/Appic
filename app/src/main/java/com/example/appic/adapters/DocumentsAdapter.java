package com.example.appic.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.appic.R;
import com.example.appic.database.models.Media;

import java.io.File;
import java.util.List;

public class DocumentsAdapter extends RecyclerView.Adapter<DocumentsAdapter.ViewHolder> {

    private List<Media> documents;
    private OnDocumentClickListener listener;

    public interface OnDocumentClickListener {
        void onDocumentClick(Media document);
        void onDeleteClick(Media document);
    }

    public DocumentsAdapter(List<Media> documents, OnDocumentClickListener listener) {
        this.documents = documents;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_document_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Media document = documents.get(position);
        File file = new File(document.getPath());
        holder.tvName.setText(file.getName());
        holder.ivIcon.setImageResource(getDocumentIcon(file));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onDocumentClick(document);
        });
        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDeleteClick(document);
        });
    }

    @Override
    public int getItemCount() {
        return documents.size();
    }

    public void updateData(List<Media> newDocuments) {
        this.documents = newDocuments;
        notifyDataSetChanged();
    }

    private int getDocumentIcon(File file) {
        if (file == null) return R.drawable.ic_generic_file;
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) return R.drawable.ic_pdf;
        if (name.endsWith(".doc") || name.endsWith(".docx")) return R.drawable.ic_word;
        if (name.endsWith(".txt")) return R.drawable.ic_txt;
        return R.drawable.ic_generic_file;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName;
        ImageView ivIcon;
        ImageView btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.document_name);
            ivIcon = itemView.findViewById(R.id.document_icon);
            btnDelete = itemView.findViewById(R.id.delete_button);
        }
    }
}