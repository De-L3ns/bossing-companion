package com.bossingcompanion.application;

import com.bossingcompanion.domain.Boss;
import com.bossingcompanion.domain.CollectionItem;
import com.bossingcompanion.domain.DropTable;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface WikiTransport
{
	CompletableFuture<DropTable> load(Boss boss, List<CollectionItem> publicDefinitions);
	void close();
}
