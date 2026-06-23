package com.example.cctest.widget;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cctest.R;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class BottomSuggestionListController {

    private final RecyclerView suggestionListView;
    private final HorizontalSuggestionAdapter suggestionAdapter;

    BottomSuggestionListController(
        Context context,
        View rootView,
        ToastRequestListener toastRequestListener
    ) {
        suggestionListView = rootView.findViewById(R.id.input_suggestion_list);
        if (suggestionListView == null) {
            suggestionAdapter = null;
            return;
        }

        suggestionAdapter = new HorizontalSuggestionAdapter(
            new ArrayList<>(),
            toastRequestListener
        );
        suggestionListView.setLayoutManager(
            new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        );
        suggestionListView.setLayoutAnimation(
            AnimationUtils.loadLayoutAnimation(
                context,
                R.anim.layout_baidu_web_suggestion_enter
            )
        );
        suggestionListView.setItemAnimator(null);
        suggestionListView.setAdapter(suggestionAdapter);
        setVisible(false);
    }

    void setVisible(boolean visible) {
        if (suggestionListView == null) {
            return;
        }
        boolean shouldAnimateEntrance = visible
            && suggestionListView.getVisibility() != View.VISIBLE;
        suggestionListView.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (shouldAnimateEntrance) {
            suggestionListView.scheduleLayoutAnimation();
        }
    }

    boolean isVisible() {
        return suggestionListView != null && suggestionListView.getVisibility() == View.VISIBLE;
    }

    void appendAdditionalSuggestions() {
        if (suggestionAdapter == null) {
            return;
        }
        suggestionAdapter.appendItems(createAdditionalSuggestions());
    }

    void ensureDefaultSuggestions() {
        if (suggestionAdapter == null || suggestionAdapter.getItemCount() > 0) {
            return;
        }
        suggestionAdapter.replaceItems(createDefaultSuggestions());
        resetScroll();
    }

    void clearSuggestions() {
        if (suggestionAdapter == null) {
            return;
        }
        suggestionAdapter.replaceItems(new ArrayList<>());
        resetScroll();
    }

    void replaceSuggestions() {
        if (suggestionAdapter == null) {
            return;
        }
        suggestionAdapter.replaceItems(createReplacementSuggestions());
        resetScroll();
        if (isVisible()) {
            suggestionListView.scheduleLayoutAnimation();
        }
    }

    private List<HorizontalSuggestionItem> createDefaultSuggestions() {
        return Arrays.asList(
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_ai_grid,
                R.string.baidu_web_suggestion_smart_insurance
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_attachment_product,
                R.string.baidu_web_suggestion_product_explain
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_attachment_image,
                R.string.baidu_web_suggestion_easy_match
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_plus,
                R.string.baidu_web_suggestion_custom_plan
            )
        );
    }

    private List<HorizontalSuggestionItem> createAdditionalSuggestions() {
        return Arrays.asList(
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_attachment_product,
                R.string.baidu_web_suggestion_family_plan
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_ai_grid,
                R.string.baidu_web_suggestion_coverage_calculator
            )
        );
    }

    private List<HorizontalSuggestionItem> createReplacementSuggestions() {
        return Arrays.asList(
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_attachment_image,
                R.string.baidu_web_suggestion_health_notice
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_attachment_product,
                R.string.baidu_web_suggestion_claim_assist
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_ai_grid,
                R.string.baidu_web_suggestion_policy_review
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_plus,
                R.string.baidu_web_suggestion_budget_plan
            ),
            new HorizontalSuggestionItem(
                R.drawable.ic_baidu_web_attachment_product,
                R.string.baidu_web_suggestion_renewal_reminder
            )
        );
    }

    private void resetScroll() {
        if (suggestionListView == null) {
            return;
        }
        suggestionListView.stopScroll();
        suggestionListView.scrollToPosition(0);
    }

    interface ToastRequestListener {
        void onToastRequested(int messageResource, Object... formatArgs);
    }

    private static class HorizontalSuggestionItem {
        private final int iconResource;
        private final int textResource;

        private HorizontalSuggestionItem(int iconResource, int textResource) {
            this.iconResource = iconResource;
            this.textResource = textResource;
        }
    }

    private static class HorizontalSuggestionAdapter
        extends RecyclerView.Adapter<HorizontalSuggestionViewHolder> {

        private final List<HorizontalSuggestionItem> items;
        private final ToastRequestListener toastRequestListener;

        private HorizontalSuggestionAdapter(
            List<HorizontalSuggestionItem> items,
            ToastRequestListener toastRequestListener
        ) {
            this.items = new ArrayList<>(items);
            this.toastRequestListener = toastRequestListener;
        }

        private void appendItems(List<HorizontalSuggestionItem> newItems) {
            int startPosition = items.size();
            items.addAll(newItems);
            notifyItemRangeInserted(startPosition, newItems.size());
        }

        private void replaceItems(List<HorizontalSuggestionItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @Override
        public HorizontalSuggestionViewHolder onCreateViewHolder(
            ViewGroup parent,
            int viewType
        ) {
            View itemView = LayoutInflater.from(parent.getContext()).inflate(
                R.layout.item_baidu_web_suggestion_chip,
                parent,
                false
            );
            return new HorizontalSuggestionViewHolder(itemView);
        }

        @Override
        public void onBindViewHolder(HorizontalSuggestionViewHolder holder, int position) {
            holder.bind(items.get(position), toastRequestListener);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class HorizontalSuggestionViewHolder extends RecyclerView.ViewHolder {
        private final ImageView iconView;
        private final TextView textView;

        private HorizontalSuggestionViewHolder(View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.suggestion_chip_icon);
            textView = itemView.findViewById(R.id.suggestion_chip_text);
        }

        private void bind(
            HorizontalSuggestionItem item,
            ToastRequestListener toastRequestListener
        ) {
            iconView.setImageResource(item.iconResource);
            textView.setText(item.textResource);
            itemView.setContentDescription(textView.getText());
            itemView.setOnClickListener(view -> {
                if (toastRequestListener != null) {
                    toastRequestListener.onToastRequested(item.textResource);
                }
            });
        }
    }
}
