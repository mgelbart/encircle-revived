function make_clean(dataset)

delete(sprintf('../meta/%s.txt', dataset)); 
delete(sprintf('../images/%s/*', dataset));
delete(sprintf('../polygons/%s/*', dataset));
delete(sprintf('../segment_likelihoods/%s/*', dataset));