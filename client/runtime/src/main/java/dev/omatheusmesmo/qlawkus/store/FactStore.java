package dev.omatheusmesmo.qlawkus.store;

import java.util.List;
import java.util.Map;

public interface FactStore {

  void store(String content, Map<String, Object> metadata);

  List<String> search(String query, int maxResults, double minScore);

  List<String> listSources();

  long purgeBySource(String source);

  long purgeAll();
}
