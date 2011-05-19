clear all; close all; clc;

% read in the sample image (in current directory)
x = double(imread('fly3.tif'))/256;

% call the script I use for EDGE
% the last parameter is the threshold. 0 is the default value.
% here, we use a higher threshold to produce an undersegmentation
y = get_membs_v3(x, 20, 70, .5);
 
% show the undersegmentation as an overlay over the original
% out(:,:,1) = x;
% out(:,:,3) = y;
% figure, imshow(out);

% label the regions and find the areas
z  = bwlabel(~y, 4);
a = regionprops(z, 'Area');
a = [a.Area];

% then, sort regions by area and remove the first two (background and smth)
s = flipud(sortrows([a(:) (1:length(a))']));
s(1:2,:) = [];

%% write the images to a file



for i = 1:1%size(s, 1)
    % make a binary mask for region i
    mask = z==s(i,2);
    
    % extract the portion of the original image inside the mask
    patch = x .* double(mask);
    
    % crop to a rectangular bounding box
    [yy xx] = find(patch);
    patch_crop = patch(min(yy):max(yy),min(xx):max(xx));
    mask_crop = mask(min(yy):max(yy),min(xx):max(xx));
    
    % find the contour around the mask
    C = contourc(mask_crop, [0.5 0.5]);
    
    % display the image and get the label
    h1 = figure(1);
    imshow(patch_crop);
    k = waitforbuttonpress;
    label = str2double(get(h1, 'CurrentCharacter'));
    
    % if you want to discard the image
    if isnan(label)
        continue;
    end
    
    % write the image to a file
    imwrite(patch_crop, sprintf('../images/fly/%04u.jpg', i), 'jpg');
    
    % write the contour to a file
    polygon_file = fopen(sprintf('../polygons/fly/%04u.txt', i), 'w');
    for j =  1:2
        for k = 1:size(C, 2);
            fprintf(polygon_file, '%.2f,', C(j, k));
        end
        fprintf(polygon_file, '\n');
    end
    fclose(polygon_file);
    
    % write an initial database entry to a file
    meta_file = fopen(sprintf('../segments_meta/fly/%04u.txt', i), 'w');
    fprintf(meta_file, '%04u.jpg\n', i);
    fprintf(meta_file, '%u:%.2f\n,', label, 1);
    fprintf(meta_file, '\n');
    fclose(meta_file);
    
end
    
    
%%
%figure, imshow(z)

%figure, imshow(y(170:320, 420:670))


% sz = 5;
% blurred = filter2(ones(sz)/sz/sz, x);
% 
% figure, imshow(blurred)
% 
% bw = blurred > .17;
% 
% figure, imshow(bw)
% 
% %bw2 = bwmorph(bw, 'close', Inf);
% % sz = 5;
% % se = strel(ones(sz));
% % bw = imerode(imdilate(bw, se), se);
% % 
% % figure, imshow(bw)
% 
% bw3 = bwmorph(bw, 'shrink', Inf);
% bw3 = bwmorph(bw3, 'clean');
% 
% imshow(bw3)