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

save temp;

%%
load temp


meta_file = fopen('../meta/fly.txt', 'w');

LABELING_RELIABILITY = 0.9;
% allows the user to label the images
% writes the images, labels, polygon contours to their respective files
for i = 1:size(s, 1)
    % make a binary mask for region i
    mask = z==s(i,2);
    
    % extract the portion of the original image inside the mask
    patch = x .* double(mask);
    
    % crop to a rectangular bounding box
    [yy xx] = find(patch);
    PAD = 1; 
    patch_crop = patch(min(yy):max(yy),min(xx):max(xx));
    mask_crop = mask(min(yy):max(yy),min(xx):max(xx));
    
    patch_crop_new = zeros(size(patch_crop)+2*PAD);
    mask_crop_new  = zeros(size(mask_crop)+2*PAD);
    
    patch_crop_new(1+PAD:end-PAD,1+PAD:end-PAD) = patch_crop;
    mask_crop_new(1+PAD:end-PAD,1+PAD:end-PAD) = mask_crop;
    
    patch_crop = patch_crop_new;
    mask_crop = mask_crop_new;
    
    % find the contour around the mask
    C = contourc(mask_crop, [0.5 0.5]);
    C(:,1) = [];
    % reduce the number of contour points by imposing a maximum angle  
    % on three adjacent points
  
    
MAXIMUM_NUMBER_OF_POINTS_IN_THE_POLYGON_THAT_REPRESENTS_THINGS = 45;
while size(C,2) > MAXIMUM_NUMBER_OF_POINTS_IN_THE_POLYGON_THAT_REPRESENTS_THINGS
    windowSize = 3;
    Cfilter = filter(ones(1,windowSize)/windowSize, 1, C.').';
    Cfilter = Cfilter(:,windowSize:end-(windowSize-2));
    C = Cfilter;
    
    Ccopy = C;
    MIN_ANGLE = 15;  % angles of less than this many degrees are removed
    for j = size(C, 2)-1:-1:2
        xy = C(:,j-1:j+1);
        xy = bsxfun(@minus, xy, xy(:,2));  % middle point becomes origin
        theta1 = atan2(xy(2,1),xy(1,1));
        theta3 = atan2(xy(2,3),xy(1,3));
        theta = abs(theta1-theta3);
        theta = abs(theta - pi);
        theta = theta*180/pi;
        if theta < MIN_ANGLE
            Ccopy(:,j) = [];
            fprintf('removed angle %f\n', theta);
        else
            fprintf('kept angle %f\n', theta);
        end
        
    end
    C = Ccopy;
    
end
keyboard
    
    % the above doesn't work so well, because it's hard to get rid of
    % useless points. for now, just get convex hull
%     R = regionprops(mask_crop, 'ConvexHull');
%     C = R.ConvexHull.';
%     Ccopy = C;
%     MIN_ANGLE = 20;
%     for j = size(C, 2)-1:-1:2
%         xy = C(:,j-1:j+1);
%         xy = bsxfun(@minus, xy, xy(:,2));  % middle point becomes origin
%         theta1 = atan2(xy(2,1),xy(1,1));
%         theta3 = atan2(xy(2,3),xy(1,3));
%         theta = abs(theta1-theta3);
%         theta = abs(theta - pi);
%         theta = theta*180/pi;
%         if theta < MIN_ANGLE
%             Ccopy(:,j) = [];
%             fprintf('removed angle %f\n', theta);
%         else
%             fprintf('kept angle %f\n', theta);
%         end
%         
%     end
    
    keyboard
    
    % display the image and get the label
    h1 = figure(1);
    imshow(patch_crop);
    title(sprintf('%u of %u', i, size(s, 1)));

    label = [];
    while isempty(label)
        k = waitforbuttonpress;
        label = get(h1, 'CurrentCharacter');
        if label == ' ' % if you want to discard the image
            break;
        end
        label = str2double(label);
        if ((label < 0) || (label > 10)) || ~(label > 0 && label < 10)
            label = [];
        end
    end

    fprintf(meta_file, '%04u\n', i);
    
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
    nsegments_file = fopen(sprintf('../segment_likelihoods/fly/%04u.txt', i), 'w');
    %fprintf(nsegments_file, '%04u.jpg\n', i);
    fprintf(nsegments_file, '%u:%.2f,\n', label, LABELING_RELIABILITY);
    fprintf(nsegments_file, '\n');
    fclose(nsegments_file);
    
end
close(1)
close(meta_file);
   