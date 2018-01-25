package com.hyphenate.easeui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.ImageView;
import android.widget.TextView;

import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.easeui.R;
import com.hyphenate.easeui.adapter.EaseConversationAdapater;

import net.doyouhike.app.library.ui.widgets.EnhancedListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


public class EaseConversationList extends EnhancedListView {

    protected int primaryColor;
    protected int secondaryColor;
    protected int timeColor;
    protected int primarySize;
    protected int secondarySize;
    protected float timeSize;


    protected final int MSG_REFRESH_ADAPTER_DATA = 0;

    protected Context context;
    protected EaseConversationAdapater adapter;
    protected List<EMConversation> conversations = new ArrayList<EMConversation>();
    protected List<EMConversation> passedListRef = null;


    public EaseConversationList(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public EaseConversationList(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }


    private void init(Context context, AttributeSet attrs) {
        this.context = context;
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.EaseConversationList);

        primarySize = ta.getDimensionPixelSize(R.styleable.EaseConversationList_cvsListPrimaryTextSize, 0);
        secondarySize = ta.getDimensionPixelSize(R.styleable.EaseConversationList_cvsListSecondaryTextSize, 0);
        timeSize = ta.getDimension(R.styleable.EaseConversationList_cvsListTimeTextSize, 0);

        ta.recycle();

    }

    public void init(List<EMConversation> conversationList) {
        this.init(conversationList, null);
    }

    public void init(List<EMConversation> conversationList, EaseConversationListHelper helper) {
        conversations = conversationList;
        if (helper != null) {
            this.conversationListHelper = helper;
        }
        adapter = new EaseConversationAdapater(context, 0, conversationList);
        adapter.setCvsListHelper(conversationListHelper);
//        adapter.setPrimaryColor(primaryColor);
//        adapter.setPrimarySize(primarySize);
//        adapter.setSecondaryColor(secondaryColor);
//        adapter.setSecondarySize(secondarySize);
//        adapter.setTimeColor(timeColor);
//        adapter.setTimeSize(timeSize);
        setAdapter(adapter);


        setSwipeDelete();
    }

    private void setSwipeDelete() {
        setDismissCallback(new OnDismissCallback() {
            @Override
            public Undoable onDismiss(EnhancedListView enhancedListView, final int position) {
                // Store the item for later undo
                final EMConversation item = (EMConversation) adapter.getItem(position);
                // Remove the item from the adapter
                adapter.remove(position);
                // return an Undoable
                return new Undoable() {
                    // Reinsert the item to the adapter
                    @Override
                    public void undo() {
                        adapter.insert(position, item);
                    }

                    // Return a string for your item
                    @Override
                    public String getTitle() {
                        return "删除 " + conversationListHelper.getNickByHxId(item.getUserName()); // Plz, use the resource system :)
                    }

                    // Delete item completely from your persistent storage
                    @Override
                    public void discard() {
                        EMClient.getInstance().chatManager().deleteConversation(item.getUserName(), true);
                        refresh();
                        conversationListHelper.refreshCount(item.getUserName());
                    }
                };

            }

        });
        setSwipingLayout(R.id.list_itease_layout);
        enableSwipeToDismiss();
        setSwipeDirection(SwipeDirection.END);
        setUndoHideDelay(3000);
        setRequireTouchBeforeDismiss(false);
    }


    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case MSG_REFRESH_ADAPTER_DATA:
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                    break;
                default:
                    break;
            }
        }
    };


    /**
     * load conversations
     *
     * @return +
     */
    private List<EMConversation> loadConversationsWithRecentChat() {
        Map<String, EMConversation> conversations = EMClient.getInstance().chatManager().getAllConversations();
        List<Pair<Long, EMConversation>> sortList = new ArrayList<Pair<Long, EMConversation>>();
        /**
         * lastMsgTime will change if there is new message during sorting
         * so use synchronized to make sure timestamp of last message won't change.
         */
        synchronized (conversations) {
            for (EMConversation conversation : conversations.values()) {
                if (conversation.getAllMessages().size() != 0) {
                    sortList.add(new Pair<Long, EMConversation>(conversation.getLastMessage().getMsgTime(), conversation));
                }
            }
        }
        try {
            // Internal is TimSort algorithm, has bug
            sortConversationByLastChatTime(sortList);
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<EMConversation> list = new ArrayList<EMConversation>();
        for (Pair<Long, EMConversation> sortItem : sortList) {
            list.add(sortItem.second);
        }
        return list;
    }

    /**
     * sorting according timestamp of last message
     *
     * @param conversationList
     */
    private void sortConversationByLastChatTime(List<Pair<Long, EMConversation>> conversationList) {
        Collections.sort(conversationList, new Comparator<Pair<Long, EMConversation>>() {
            @Override
            public int compare(final Pair<Long, EMConversation> con1, final Pair<Long, EMConversation> con2) {

                if (con1.first == con2.first) {
                    return 0;
                } else if (con2.first > con1.first) {
                    return 1;
                } else {
                    return -1;
                }
            }

        });
    }

    public EMConversation getItem(int position) {
        return (EMConversation) adapter.getItem(position);
    }

    public void refresh() {
        if (!handler.hasMessages(MSG_REFRESH_ADAPTER_DATA)) {
            handler.sendEmptyMessage(MSG_REFRESH_ADAPTER_DATA);
        }
    }

    public void filter(CharSequence str) {
        adapter.getFilter().filter(str);
    }


    private EaseConversationListHelper conversationListHelper;

    public interface EaseConversationListHelper {
        /**
         * set content of second line
         *
         * @param lastMessage
         * @return
         */
        String onSetItemSecondaryText(EMMessage lastMessage);

        /**
         * 设置 对话列表头像和昵称
         * @param context
         * @param imageView
         * @param textView
         * @param hxId
         */
        void setAvatarAndNick(Context context, ImageView imageView, TextView textView,String hxId);

        /**
         * 通过环信ID获取 用户昵称
         * @param hxId
         * @return
         */
        String getNickByHxId(String hxId);

        /**
         * 删除对话列表 刷新
         * @param hxId
         */
        void refreshCount(String hxId);
    }

    public void setConversationListHelper(EaseConversationListHelper helper) {
        conversationListHelper = helper;
    }
}
