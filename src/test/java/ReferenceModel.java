import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;

/**
 * [기법 2] 차등 테스트의 기준 모델.
 *
 * 값(멀티셋, 최솟값, 크기)의 기준은 java.util.PriorityQueue<Integer> 이다.
 * PriorityQueue 는 노드 식별 개념이 없으므로, decreaseKey 검증을 위해 노드 핸들 표를 따로 둔다.
 *   - 핸들: 구현 Node 객체(identity) ↔ 기대 키 / 소속 힙 / 생존 여부
 *   - keyOrder: 구현의 "값으로 decrease_key 시 먼저 찾은 노드" 계약을 재현하기 위한 키별 노드 순서
 *       삽입 → 해당 키 목록 끝에 추가
 *       감소 → 기존 키 목록에서 빼고 새 키 목록 끝에 추가 (같은 키로의 감소는 변화 없음)
 *       병합 → 키마다 (this 목록) + (other 목록) 순서
 */
final class ReferenceModel {

    static final class Handle {
        final int id;
        final Object node;
        int key;
        int owner;
        boolean live = true;

        Handle(int id, Object node, int key, int owner) {
            this.id = id;
            this.node = node;
            this.key = key;
            this.owner = owner;
        }

        @Override public String toString() {
            return "#" + id + "(key=" + key + ", heap=" + owner + (live ? "" : ", 삭제됨") + ")";
        }
    }

    private final List<PriorityQueue<Integer>> queues = new ArrayList<>();
    private final List<List<Handle>> live = new ArrayList<>();
    private final List<Map<Integer, List<Handle>>> keyOrder = new ArrayList<>();
    private final IdentityHashMap<Object, Handle> byNode = new IdentityHashMap<>();
    private int nextId;

    ReferenceModel(int heapCount) {
        for (int i = 0; i < heapCount; i++) {
            queues.add(new PriorityQueue<>());
            live.add(new ArrayList<>());
            keyOrder.add(new HashMap<>());
        }
    }

    int heapCount() { return queues.size(); }

    Handle insert(int h, Object node, int key) {
        Handle hd = new Handle(nextId++, node, key, h);
        queues.get(h).add(key);
        live.get(h).add(hd);
        keyOrder.get(h).computeIfAbsent(key, k -> new ArrayList<>()).add(hd);
        byNode.put(node, hd);
        return hd;
    }

    Integer min(int h) { return queues.get(h).peek(); }

    int size(int h) { return queues.get(h).size(); }

    List<Handle> liveHandles(int h) { return Collections.unmodifiableList(live.get(h)); }

    Handle handleOf(Object node) { return byNode.get(node); }

    Handle firstWithKey(int h, int key) {
        List<Handle> list = keyOrder.get(h).get(key);
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    List<Integer> distinctKeysSorted(int h) { return new ArrayList<>(new TreeSet<>(keyOrder.get(h).keySet())); }

    List<Integer> sortedContents(int h) {
        List<Integer> list = new ArrayList<>(queues.get(h));
        Collections.sort(list);
        return list;
    }

    void extract(Handle hd) {
        int h = hd.owner;
        queues.get(h).remove(Integer.valueOf(hd.key));
        live.get(h).remove(hd);
        removeOrder(h, hd);
        hd.live = false;
    }

    void decrease(Handle hd, int newKey) {
        if (newKey == hd.key) return;
        int h = hd.owner;
        queues.get(h).remove(Integer.valueOf(hd.key));
        queues.get(h).add(newKey);
        removeOrder(h, hd);
        hd.key = newKey;
        keyOrder.get(h).computeIfAbsent(newKey, k -> new ArrayList<>()).add(hd);
    }

    void meld(int a, int b) {
        if (a == b) return;
        queues.get(a).addAll(queues.get(b));
        queues.get(b).clear();
        for (Handle hd : live.get(b)) hd.owner = a;
        live.get(a).addAll(live.get(b));
        live.get(b).clear();
        for (Map.Entry<Integer, List<Handle>> e : keyOrder.get(b).entrySet()) {
            keyOrder.get(a).computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
        }
        keyOrder.get(b).clear();
    }

    private void removeOrder(int h, Handle hd) {
        List<Handle> list = keyOrder.get(h).get(hd.key);
        list.remove(hd);
        if (list.isEmpty()) keyOrder.get(h).remove(hd.key);
    }
}
