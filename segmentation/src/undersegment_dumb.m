clear all; close all; clc;

% read in the sample image (in current directory)
x = double(imread('fly3.tif'))/256;

% call the script I use for EDGE
% the last parameter is the threshold. 0 is the default value.
% here, we use a higher threshold to produce an undersegmentation
y = get_membs_v3(x, 20, 70, .5);
 
% show the undersegmentation as an overlay over the original
out(:,:,1) = x;
out(:,:,3) = y;
figure, imshow(out);

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
    C = countourc(mask, [0.5 0.5]);
    
    % write the image to a file
    % imwrite(patch_crop, sprintf('fly/unlabeled/%04u.jpg', i), 'jpg');
   
    % write the contour to a file
    
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