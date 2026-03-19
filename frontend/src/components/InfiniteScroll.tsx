import React, { useEffect, useRef } from 'react';

interface InfiniteScrollProps {
    onLoadMore: () => void;
    isLoading: boolean;
    hasMore: boolean;
}

export const InfiniteScroll: React.FC<InfiniteScrollProps> = ({
    onLoadMore,
    isLoading,
    hasMore
}) => {
    const observerRef = useRef<IntersectionObserver | null>(null);
    const loadMoreRef = useRef<HTMLDivElement | null>(null);
    const onLoadMoreRef = useRef(onLoadMore);

    // Keep the ref updated with the latest onLoadMore callback
    useEffect(() => {
        onLoadMoreRef.current = onLoadMore;
    }, [onLoadMore]);

    useEffect(() => {
        if (isLoading || !hasMore) return;

        const observer = new IntersectionObserver(
            (entries) => {
                // Only trigger if element is intersecting and we're not already loading
                if (entries[0].isIntersecting && !isLoading && hasMore) {
                    onLoadMoreRef.current();
                }
            },
            {
                rootMargin: '100px', // Trigger when 100px from bottom
                threshold: 0.1
            }
        );

        const currentRef = loadMoreRef.current;
        if (currentRef) {
            observer.observe(currentRef);
        }

        observerRef.current = observer;

        return () => {
            observer.disconnect();
        };
    }, [isLoading, hasMore]);

    return (
        <div
            ref={loadMoreRef}
            className="flex justify-center py-8"
        >
            {isLoading && hasMore ? (
                <div className="flex items-center space-x-2 text-neutral-500">
                    <div className="animate-spin rounded-full h-6 w-6 border-b-2 border-primary-600"></div>
                    <span className="text-sm">Loading more notes...</span>
                </div>
            ) : !hasMore ? (
                <p className="text-sm text-neutral-400">No more notes to load</p>
            ) : null}
        </div>
    );
};
