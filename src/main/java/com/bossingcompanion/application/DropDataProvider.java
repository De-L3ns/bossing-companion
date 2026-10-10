package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.domain.DropTable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Public drop snapshots; implementations never need private progress. */
public interface DropDataProvider
{
	CompletableFuture<DropTable> load(Boss boss, List<CollectionItem> definitions);
	void close();
}
